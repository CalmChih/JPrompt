package com.chih.JPrompt.spring;

import com.chih.JPrompt.core.spi.AbstractIndexBasedPromptSource;
import com.chih.JPrompt.core.support.PromptParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 基于 Spring Resource 的 Prompt 源（重构版）。
 * <p>
 * 集成 Spring Resource 抽象层，提供企业级的 Prompt 资源管理能力。
 * 通过继承 AbstractIndexBasedPromptSource，获得了 Index-Only 模式和热更新功能。
 * </p>
 *
 * <h3>核心特性：</h3>
 * <ul>
 *   <li><strong>Spring 生态集成</strong>：完全兼容 Spring Resource 抽象</li>
 *   <li><strong>通配符支持</strong>：支持 classpath 通配符和 ant-style 路径模式</li>
 *   <li><strong>协议无关</strong>：统一处理 file:、classpath:、jar: 等协议</li>
 *   <li><strong>Index-Only 模式</strong>：零 OOM 风险的高性能索引架构</li>
 *   <li><strong>热更新支持</strong>：自动检测文件变更并增量更新</li>
 *   <li><strong>生命周期管理</strong>：实现 DisposableBean，确保资源正确释放</li>
 * </ul>
 *
 * <h3>支持的位置模式：</h3>
 * <ul>
 *   <li>Classpath 资源（包括所有 jar 包）：classpath:/prompts/*.yaml</li>
 *   <li>文件系统资源：file:/opt/app/prompts/*.yml</li>
 *   <li>相对路径：<code>prompts/</code></li>
 *   <li>混合模式：<code>classpath:/default-prompts/,file:/custom-prompts/</code></li>
 * </ul>
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li><strong>微服务应用</strong>：统一的 Prompt 资源管理</li>
 *   <li><strong>容器化部署</strong>：支持外部配置卷挂载</li>
 *   <li><strong>多环境配置</strong>：通过 Spring Profile 区分环境</li>
 *   <li><strong>企业集成</strong>：与 Spring Config、Cloud Config 等集成</li>
 * </ul>
 *
 * @author lizhiyuan
 * @see org.springframework.core.io.Resource
 * @see AbstractIndexBasedPromptSource
 */
public class SpringResourcePromptSource extends AbstractIndexBasedPromptSource<Resource>
        implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SpringResourcePromptSource.class);

    /**
     * Spring 资源模式解析器
     * <p>
     * 使用 PathMatchingResourcePatternResolver 支持：
     * 1. Ant-style 路径模式：双星号匹配任意层级路径
     * 2. classpath*: 前缀：扫描所有 jar 包中的资源
     * 3. file: 协议：访问文件系统资源
     * </p>
     */
    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * 资源位置配置列表
     * <p>
     * 存储用户配置的资源位置模式，支持多种协议和路径格式。
     * 列表在构造时确定，运行时不可变，确保线程安全。
     * </p>
     */
    private final List<String> locations;

    /**
     * 构造函数（完整配置版本）
     *
     * 创建 SpringResourcePromptSource 实例，提供完整的配置选项。
     * 适合生产环境使用，支持自定义线程池和防抖配置。
     *
     * @param locations 资源位置列表，支持 Spring Resource 路径模式
     * @param debounceDelayMs 防抖延迟时间（毫秒），建议 500-2000ms
     * @param watcherExecutor 文件监听线程池（可选）
     * @param debounceExecutor 防抖定时器线程池（可选）
     */
    public SpringResourcePromptSource(List<String> locations, long debounceDelayMs,
                                    ExecutorService watcherExecutor, ScheduledExecutorService debounceExecutor) {
        super(debounceDelayMs, watcherExecutor, debounceExecutor);

        /* 保存位置配置的不可变副本 */
        this.locations = new ArrayList<>(locations);

        /* 1. 首次全量加载：扫描所有位置并建立索引 */
        initialLoad();

        /* 2. 启动文件监听：开启热更新支持 */
        startWatcher();
    }

    /**
     * 首次加载：扫描所有路径并填充缓存
     */
    private void initialLoad() {
        /* 用于避免重复注册同一个目录 */
        Set<File> watchedDirectories = new HashSet<>();

        for (String location : locations) {
            if (!StringUtils.hasText(location)) {
                continue;
            }

            try {
                /* 支持通配符模式 */
                Resource[] resources = resolver.getResources(location);
                for (Resource resource : resources) {
                    if (isPromptResource(resource)) {
                        loadResource(resource);

                        /* 注册文件监听器（只对文件系统资源） */
                        try {
                            if (isFileResource(resource)) {
                                File parentFile = resource.getFile().getParentFile();
                                if (parentFile != null && watchedDirectories.add(parentFile)) {
                                    fileWatcher.register(parentFile);
                                    logWatcherRegistered(parentFile.getAbsolutePath());
                                }
                            }
                        } catch (IOException e) {
                            logWatcherRegistrationFailure(resource.getDescription(), e);
                        }
                    }
                }
            } catch (IOException e) {
                logResourceScanFailure(location, e);
            }
        }
    }

    /**
     * 加载单个资源
     */
    private void loadResource(Resource resource) {
        safeLoadResource(resource);
    }

    /**
     * 判断是否为 Prompt 资源
     */
    private boolean isPromptResource(Resource resource) {
        try {
            if (resource == null) {
                return false;
            }

            String filename = resource.getFilename();
            if (filename == null) {
                return false;
            }

            return PromptParser.isSupportedFile(filename);
        } catch (Exception e) {
            log.warn("Failed to determine if resource is a prompt resource: {}", resource.getDescription(), e);
            return false;
        }
    }

    // === 实现抽象方法 ===

    @Override
    protected InputStream openStream(Resource resource) throws Exception {
        return resource.getInputStream();
    }

    @Override
    protected String getResourceId(Resource resource) {
        return getResourceCacheKey(resource);
    }

    @Override
    protected boolean exists(Resource resource) {
        try {
            return resource.exists();
        } catch (Exception e) {
            log.warn("Failed to check resource existence: {}", resource.getDescription(), e);
            return false;
        }
    }

    @Override
    protected Resource resolveResourceFromFile(File file) {
        // 将 File 转换为 Spring Resource
        return new FileSystemResource(file);
    }

    @Override
    protected String getResourceDescription(Resource resource) {
        return resource.getDescription();
    }

    @Override
    protected String getResourceFilename(Resource resource) {
        try {
            // Spring Resource 提供了直接的 getFilename() 方法
            String filename = resource.getFilename();
            if (filename != null) {
                return filename;
            }

            // 如果 getFilename() 返回 null，尝试从 URI 中提取
            String uri = resource.getURI().toString();
            int lastSlash = uri.lastIndexOf('/');
            return lastSlash >= 0 ? uri.substring(lastSlash + 1) : uri;
        } catch (Exception e) {
            // 备用方案：使用描述
            String description = resource.getDescription();
            int lastSlash = Math.max(description.lastIndexOf('/'), description.lastIndexOf('\\'));
            return lastSlash >= 0 ? description.substring(lastSlash + 1) : description;
        }
    }

    // getCurrentResource 方法已在基类中实现，使用统一的 resourceCache

    // === 辅助方法 ===

    /**
     * 获取资源缓存键
     */
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

    /**
     * 判断是否为文件资源
     */
    private boolean isFileResource(Resource resource) {
        try {
            return resource.isFile() || "file".equals(resource.getURL().getProtocol());
        } catch (IOException e) {
            return false;
        }
    }

    
    // === DisposableBean 实现 ===

    @Override
    public void destroy() {
        try {
            close();
        } catch (Exception e) {
            log.error("Failed to destroy SpringResourcePromptSource", e);
        }
    }
}