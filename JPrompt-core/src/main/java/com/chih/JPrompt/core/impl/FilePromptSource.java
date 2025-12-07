package com.chih.JPrompt.core.impl;

import com.chih.JPrompt.core.spi.AbstractIndexBasedPromptSource;
import com.chih.JPrompt.core.support.FileResource;
import com.chih.JPrompt.core.support.PromptParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * 基于文件的 Prompt 源实现（重构版）
 * <p>
 * 特性：
 * 1. 继承 AbstractIndexBasedPromptSource，获得 Index-Only 模式能力
 * 2. 支持文件系统和 Classpath 资源混合加载
 * 3. 热更新支持
 * 4. 零 OOM 风险的内存管理
 * 5. 支持自定义线程池和防抖配置
 * </p>
 * <p>
 * <strong>使用示例：</strong>
 * <pre>{@code
 * // 使用默认配置
 * FilePromptSource source1 = new FilePromptSource("/prompts");
 *
 * // 使用自定义防抖时间
 * FilePromptSource source2 = new FilePromptSource("/prompts", 1000L, null, null);
 *
 * // 使用自定义线程池（推荐在生产环境中）
 * ExecutorService watcherExecutor = Executors.newFixedThreadPool(2);
 * ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor();
 * FilePromptSource source3 = new FilePromptSource("/prompts", 500L, watcherExecutor, debounceExecutor);
 * }</pre>
 * </p>
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
public class FilePromptSource extends AbstractIndexBasedPromptSource<FileResource> {

    private static final Logger log = LoggerFactory.getLogger(FilePromptSource.class);

    // 支持多个路径（可能是文件，也可能是目录）
    private final List<String> configPaths;
    
    /**
     * 构造函数（支持传入多个路径）
     *
     * 使用可变参数创建 FilePromptSource，支持同时监控多个文件或目录。
     * 使用默认配置：防抖延迟 500ms，内部管理的线程池。
     *
     * <h3>使用示例：</h3>
     * <pre>{@code
     * // 监控单个文件
     * FilePromptSource source1 = new FilePromptSource("/prompts/greeting.yaml");
     *
     * // 监控多个文件
     * FilePromptSource source2 = new FilePromptSource(
     *     "/prompts/greeting.yaml",
     *     "/prompts/farewell.yaml",
     *     "/prompts/chat/"
     * );
     * }</pre>
     *
     * @param paths 文件或目录路径列表，支持文件系统路径和 Classpath 路径
     */
    public FilePromptSource(String... paths) {
        this(Arrays.asList(paths));
    }

    /**
     * 构造函数（使用默认防抖时间和线程池）
     *
     * 使用列表形式的路径创建 FilePromptSource，提供更灵活的路径管理。
     * 使用默认配置：防抖延迟 500ms，内部管理的线程池。
     *
     * <h3>路径类型说明：</h3>
     * <ul>
     *   <li>绝对路径：/home/user/prompts 或 C:\prompts</li>
     *   <li>相对路径：prompts/ 或 ./prompts</li>
     *   <li>Classpath：/prompts（自动从 Classpath 加载）</li>
     * </ul>
     *
     * @param paths 文件路径列表，可以是文件或目录路径
     */
    public FilePromptSource(List<String> paths) {
        this(paths, 500L, null, null);
    }

    /**
     * 构造函数（支持自定义防抖时间和线程池）
     *
     * 提供完整的配置选项，适合生产环境使用。推荐在生产环境中提供自定义线程池，
     * 以便更好地控制线程生命周期和资源使用。
     *
     * <h3>生产环境推荐配置：</h3>
     * <pre>{@code
     * // 创建专用线程池
     * ExecutorService watcherExecutor = Executors.newFixedThreadPool(2);
     * ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor();
     *
     * // 配置 FilePromptSource
     * FilePromptSource source = new FilePromptSource(
     *     Arrays.asList("/prompts"),
     *     1000L,  // 1秒防抖，适合频繁变更的场景
     *     watcherExecutor,
     *     debounceExecutor
     * );
     *
     * // 应用退出时关闭线程池
     * Runtime.getRuntime().addShutdownHook(new Thread(() -> {
     *     watcherExecutor.shutdown();
     *     debounceExecutor.shutdown();
     * }));
     * }</pre>
     *
     * @param paths 文件路径列表，支持文件系统路径和 Classpath 路径
     * @param debounceDelayMs 防抖延迟时间（毫秒），建议值 100-2000ms
     * @param watcherExecutor 文件监听线程池（可选），为 null 时内部创建
     * @param debounceExecutor 防抖定时器线程池（可选），为 null 时内部创建
     */
    public FilePromptSource(List<String> paths, long debounceDelayMs,
                           ExecutorService watcherExecutor, ScheduledExecutorService debounceExecutor) {
        super(debounceDelayMs, watcherExecutor, debounceExecutor);

        // 创建配置路径的副本，避免外部修改影响内部状态
        this.configPaths = new ArrayList<>(paths);

        // 1. 首次加载所有资源并注册文件监听器
        initialLoadAndWatch();

        // 2. 启动文件监听线程，开始热更新支持
        startWatcher();
    }

    /**
     * 遍历配置路径，加载文件并注册监听
     */
    private void initialLoadAndWatch() {
        // 用于避免重复注册同一个目录
        Set<File> watchedDirectories = new HashSet<>();

        for (String pathStr : configPaths) {
            File file = new File(pathStr);

            // 1. 如果是文件系统存在的路径 (File/Dir)
            if (file.exists()) {
                if (file.isDirectory()) {
                    // 情况 A: 目录 -> 扫描目录下所有文件 + 监听目录
                    loadDirectory(file, watchedDirectories);
                    if (watchedDirectories.add(file)) {
                        fileWatcher.register(file);
                        logWatcherRegistered(file.getAbsolutePath());
                    }
                } else {
                    // 情况 B: 单文件 -> 加载文件 + 监听父目录
                    loadSingleFile(file);
                    File parentFile = file.getParentFile();
                    if (parentFile != null && watchedDirectories.add(parentFile)) {
                        fileWatcher.register(parentFile);
                        logWatcherRegistered(parentFile.getAbsolutePath());
                    }
                }
            }
            // 2. 如果文件系统不存在，尝试作为 Classpath 资源加载 (只读)
            else {
                scanClasspath(pathStr);
            }
        }
    }

    /**
     * 加载目录下的所有支持文件
     */
    private void loadDirectory(File directory, Set<File> watchedDirectories) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归加载子目录
                loadDirectory(file, watchedDirectories);
                if (watchedDirectories.add(file)) {
                    fileWatcher.register(file);
                    logWatcherRegistered(file.getAbsolutePath());
                }
            } else {
                if (PromptParser.isSupportedFile(file.getName())) {
                    loadSingleFile(file);
                }
            }
        }
    }

    /**
     * 加载单个文件
     */
    private void loadSingleFile(File file) {
        if (!PromptParser.isSupportedFile(file.getName())) {
            return;
        }

        try {
            Path filePath = file.toPath();
            FileResource resource = FileResource.fromFile(filePath);
            safeLoadResource(resource);
        } catch (Exception e) {
            logResourceLoadFailure(file.getAbsolutePath(), e);
        }
    }

    /**
     * 扫描 Classpath 资源（支持目录和 JAR）
     */
    private void scanClasspath(String path) {
        // ClassLoader 资源路径不应以 / 开头
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;

        // 优化：空路径警告处理，避免全量扫描 Classpath 导致的性能问题
        if (cleanPath.isEmpty()) {
            log.warn("Empty classpath path detected. This may cause performance issues. " +
                    "Please specify a concrete directory like 'prompts/' instead of empty string.");
            return;
        }

        try {
            Enumeration<URL> resources = this.getClass().getClassLoader().getResources(cleanPath);
            boolean found = false;

            while (resources.hasMoreElements()) {
                found = true;
                URL url = resources.nextElement();
                String protocol = url.getProtocol();

                if ("file".equals(protocol)) {
                    // 场景 A: 开发环境（资源在 target/classes 目录下）
                    File file = new File(url.toURI());
                    if (file.isDirectory()) {
                        // Classpath 资源扫描使用临时的 watchedDirectories 避免重复注册
                        Set<File> classpathWatchedDirs = new HashSet<>();
                        loadDirectory(file, classpathWatchedDirs);
                        if (classpathWatchedDirs.add(file)) {
                            fileWatcher.register(file);
                            logWatcherRegistered(file.getAbsolutePath());
                        }
                    } else {
                        loadSingleFile(file);
                    }
                } else if ("jar".equals(protocol)) {
                    // 场景 B: 生产环境（资源在 JAR 包内）
                    scanJar(url, cleanPath);
                }
            }

            if (!found) {
                // 兜底：尝试作为单文件流加载
                loadFromClasspath(cleanPath);
            }

        } catch (Exception e) {
            logResourceScanFailure(path, e);
        }
    }

    /**
     * 扫描 JAR 包内的资源
     *
     * Windows 兼容性优化：使用 try-with-resources 确保 JarFile 及时关闭，
     * 避免文件句柄锁定问题。
     *
     * 修复 JAR URL 构造错误，使用 JarURLConnection 获取基础 JAR URL。
     */
    private void scanJar(URL url, String rootPath) throws IOException {
        // URL 格式通常为: jar:file:/path/to/app.jar!/prompts
        JarURLConnection jarConn = (JarURLConnection) url.openConnection();
        jarConn.setUseCaches(false);

        // 关键修复：获取 JAR 文件的基础 URL，而不是使用 url.getPath()
        // url.getPath() 会返回 file:/path/to/app.jar!/prompts，导致重复路径
        URL jarBaseUrl = jarConn.getJarFileURL();

        // 使用 try-with-resources 确保 JarFile 在处理完成后自动关闭
        try (JarFile jarFile = jarConn.getJarFile()) {
            Enumeration<JarEntry> entries = jarFile.entries();

            // 直接处理所有符合条件的 JAR 条目
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                // 过滤逻辑：
                // 1. 必须以 rootPath 开头
                // 2. 不能是目录
                // 3. 必须是支持的文件后缀
                if (name.startsWith(rootPath) && !entry.isDirectory() && PromptParser.isSupportedFile(name)) {
                    log.debug("Processing JAR entry: {}", name);

                    try {
                        // 修复 JAR URL 构造：使用基础 JAR URL + Entry 名称
                        // 正确格式：jar:file:/path/to/app.jar!/prompts/greeting.yaml
                        String entryUrlPath = "jar:" + jarBaseUrl.toString() + "!/" + name;
                        URL entryUrl = new URL(entryUrlPath);
                        FileResource resource = FileResource.fromClasspath(entryUrl, "classpath:" + name);

                        // 直接使用父类的安全加载方法处理资源
                        safeLoadResource(resource);

                    } catch (Exception e) {
                        log.error("Failed to process JAR entry: {}", name, e);
                    }
                }
            }
        }
    }

    /**
     * 从 Classpath 加载单个资源
     */
    private void loadFromClasspath(String path) {
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                log.info("Loading classpath resource: {}", path);

                URL url = this.getClass().getClassLoader().getResource(path);
                if (url != null) {
                    FileResource resource = FileResource.fromClasspath(url, "classpath:" + path);
                    safeLoadResource(resource);
                }
            } else {
                log.warn("Prompt path not found (checked File System and Classpath): {}", path);
            }
        } catch (Exception e) {
            logResourceLoadFailure("classpath:" + path, e);
        }
    }

    // === 实现抽象方法 ===

    @Override
    protected InputStream openStream(FileResource resource) throws Exception {
        return resource.getInputStream();
    }

    @Override
    protected String getResourceId(FileResource resource) {
        return resource.getId();
    }

    @Override
    protected boolean exists(FileResource resource) {
        return resource.exists();
    }

    @Override
    protected FileResource resolveResourceFromFile(File file) {
        try {
            Path filePath = file.toPath();
            return FileResource.fromFile(filePath);
        } catch (Exception e) {
            logResourceLoadFailure(file.getAbsolutePath(), e);
            return null;
        }
    }

    @Override
    protected String getResourceDescription(FileResource resource) {
        return resource.getResourcePath();
    }

    @Override
    protected String getResourceFilename(FileResource resource) {
        // 直接使用 FileResource 提供的文件名方法
        return resource.getFilename();
    }
}