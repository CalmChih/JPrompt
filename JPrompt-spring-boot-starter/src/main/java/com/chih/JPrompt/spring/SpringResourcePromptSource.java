package com.chih.JPrompt.spring;

import com.chih.JPrompt.core.domain.PromptMeta;
import com.chih.JPrompt.core.spi.PromptSource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 Spring Resource 的 Prompt 源 (生产级实现)
 * <p>
 * 特性：
 * 1. 支持 classpath 和 file 协议的混合扫描
 * 2. 集成 NIO WatchService 实现高性能文件监听
 * 3. 实现 Debounce (防抖) 机制，防止编辑器保存时的事件抖动导致重复重载
 * 4. 实现 DisposableBean，确保应用退出时资源释放
 * </p>
 *
 * @author lizhiyuan
 */
public class SpringResourcePromptSource implements PromptSource, DisposableBean {
    
    private static final Logger log = LoggerFactory.getLogger(SpringResourcePromptSource.class);
    
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    
    private final List<String> locations;
    private volatile Runnable callback;
    
    // === 增量更新缓存 ===
    // Key: 文件绝对路径 (或 JAR 资源 URI), Value: 该文件包含的所有 Prompt
    private final Map<String, Map<String, PromptMeta>> resourceCache = new ConcurrentHashMap<>();
    
    // WatchService 相关
    private WatchService watchService;
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
    private final Set<String> watchedPaths = ConcurrentHashMap.newKeySet();
    
    // 线程池：一个用于运行 Watcher 循环，一个用于执行防抖延迟任务
    private final ExecutorService watcherExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "JPrompt-NIO-Watcher");
        t.setDaemon(true);
        return t;
    });
    
    private final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "JPrompt-Debouncer");
        t.setDaemon(true);
        return t;
    });
    
    // 防抖任务引用
    private ScheduledFuture<?> debounceTask;
    // 防抖时间窗口 (毫秒)
    private static final long DEBOUNCE_DELAY_MS = 500;
    
    // 运行状态标志
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    // 正则匹配 FrontMatter: 匹配以 --- 开头，中间是 YAML，以 --- 结尾的块
    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL);
    
    public SpringResourcePromptSource(List<String> locations) {
        this.locations = locations;
        // 1. 首次全量加载
        initialLoad();
        // 2. 启动监听
        initWatchService();
    }
    
    /**
     * 首次加载：扫描所有路径并填充缓存
     */
    private void initialLoad() {
        for (String location : locations) {
            if (!StringUtils.hasText(location)) {
                continue;
            }
            try {
                Resource[] resources = resolver.getResources(location);
                for (Resource resource : resources) {
                    loadAndCacheResource(resource);
                }
            } catch (IOException e) {
                log.warn("Failed to scan prompt location: {}", location);
            }
        }
    }
    
    /**
     * 单个资源加载逻辑 (复用于首次加载和热更新)
     */
    private void loadAndCacheResource(Resource resource) {
        if (!resource.exists()) {
            return;
        }
        
        try {
            String cacheKey = getResourceCacheKey(resource);
            Map<String, PromptMeta> loaded = parseResource(resource);
            
            if (loaded != null && !loaded.isEmpty()) {
                // 校验
                loaded.values().forEach(PromptMeta::validate);
                // 更新缓存
                resourceCache.put(cacheKey, loaded);
                // 注册监听 (仅文件)
                if (isFileResource(resource)) {
                    registerWatch(resource.getFile().getParentFile());
                }
            }
        } catch (Exception e) {
            log.error("Error loading prompt file: {}", resource.getDescription(), e);
            // 注意：这里不移除旧缓存，防止因为一次编辑错误导致服务不可用
        }
    }
    
    /**
     * 获取所有 Prompts (直接合并缓存，速度极快)
     */
    @Override
    public Map<String, PromptMeta> loadAll() {
        Map<String, PromptMeta> allPrompts = new HashMap<>();
        resourceCache.values().forEach(allPrompts::putAll);
        return allPrompts;
    }
    
    // === 解析逻辑 ===
    
    private Map<String, PromptMeta> parseResource(Resource resource) throws IOException {
        String filename = resource.getFilename();
        if (filename == null) {
            return Collections.emptyMap();
        }
        
        if (filename.endsWith(".yaml") || filename.endsWith(".yml") || filename.endsWith(".json")) {
            return mapper.readValue(resource.getInputStream(), new TypeReference<>() {});
        } else if (filename.endsWith(".md")) {
            return parseMarkdown(resource);
        }
        return Collections.emptyMap();
    }
    
    private Map<String, PromptMeta> parseMarkdown(Resource resource) throws IOException {
        String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        // 统一换行符
        content = content.replace("\r\n", "\n");
        
        PromptMeta meta = new PromptMeta();
        String templateBody = content;
        
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(content);
        if (matcher.find()) {
            String yamlPart = matcher.group(1);
            templateBody = matcher.group(2).trim();
            if (StringUtils.hasText(yamlPart)) {
                meta = mapper.readValue(yamlPart, PromptMeta.class);
            }
        }
        
        meta.setTemplate(templateBody);
        
        // ID 兜底策略
        if (!StringUtils.hasText(meta.getId())) {
            String filename = resource.getFilename();
            if (filename != null && filename.contains(".")) {
                meta.setId(filename.substring(0, filename.lastIndexOf('.')));
            }
        }
        
        return Collections.singletonMap(meta.getId(), meta);
    }
    
    // === 热更新逻辑 (增量更新) ===
    
    private void initWatchService() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            watcherExecutor.submit(this::watchLoop);
        } catch (IOException e) {
            log.error("Failed to initialize NIO WatchService", e);
        }
    }
    
    private void watchLoop() {
        log.info("JPrompt NIO Watcher started.");
        while (running.get()) {
            try {
                WatchKey key = watchService.take();
                Path dir = watchKeys.get(key);
                if (dir == null) {
                    key.cancel();
                    continue;
                }
                
                boolean needCallback = false;
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    Path changedPath = (Path) event.context();
                    String fileName = changedPath.toString();
                    
                    if (shouldIgnoreFile(fileName)) {
                        continue;
                    }
                    
                    // 构造变动文件的完整路径
                    File changedFile = dir.resolve(changedPath).toFile();
                    if (changedFile.exists()) {
                        log.debug("Reloading changed file: {}", changedFile.getAbsolutePath());
                        // 增量加载：只重新解析这一个文件，更新缓存
                        loadAndCacheResource(new FileSystemResource(changedFile));
                        needCallback = true;
                    }
                }
                
                if (needCallback) {
                    triggerDebouncedReload();
                }
                
                if (!key.reset()) {
                    watchKeys.remove(key);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Watcher loop error", e);
            }
        }
    }
    
    private boolean shouldIgnoreFile(String fileName) {
        return fileName.endsWith("~") || fileName.startsWith(".") ||
                (!fileName.endsWith(".yaml") && !fileName.endsWith(".yml") &&
                        !fileName.endsWith(".json") && !fileName.endsWith(".md"));
    }
    
    /**
     * 防抖触发器
     * 原理：每次触发都取消上一次未执行的任务，重新调度一个新的任务
     */
    private synchronized void triggerDebouncedReload() {
        if (callback == null) {
            return;
        }
        if (debounceTask != null && !debounceTask.isDone()) {
            debounceTask.cancel(false);
        }
        
        debounceTask = debounceExecutor.schedule(() -> {
            try {
                log.info("Applying incremental updates...");
                callback.run(); // 通知 Manager 重新获取（此时 loadAll 只是合并内存 Map，极快）
            } catch (Exception e) {
                log.error("Hot reload callback failed", e);
            }
        }, DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS);
    }
    
    // === 辅助方法 ===
    
    private String getResourceCacheKey(Resource resource) {
        try {
            if (isFileResource(resource)) {
                return resource.getFile().getAbsolutePath();
            }
            return resource.getURI().toString();
        } catch (IOException e) {
            return resource.getDescription();
        }
    }
    
    private boolean isFileResource(Resource resource) {
        try {
            return resource.isFile() || "file".equals(resource.getURL().getProtocol());
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 注册目录监听
     */
    private void registerWatch(File directory) {
        if (watchService == null || directory == null || !directory.exists()) {
            return;
        }
        try {
            String path = directory.getCanonicalPath();
            if (watchedPaths.contains(path)) {
                return;
            }
            
            Path dirPath = directory.toPath();
            WatchKey key = dirPath.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
            
            watchKeys.put(key, dirPath);
            watchedPaths.add(path);
            log.info("Watching directory: {}", path);
        } catch (IOException e) {
            log.warn("Failed to watch directory: {}", directory, e);
        }
    }
    
    @Override
    public void onChange(Runnable callback) {
        this.callback = callback;
    }
    
    /**
     * 销毁资源 (Spring 容器关闭时调用)
     */
    @Override
    public void destroy() {
        running.set(false);
        
        // 1. 关闭 WatchService (这会导致 watchService.take() 抛出 ClosedWatchServiceException 从而退出循环)
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                // ignore
            }
        }
        
        // 2. 关闭线程池
        watcherExecutor.shutdownNow();
        debounceExecutor.shutdownNow();
    }
}