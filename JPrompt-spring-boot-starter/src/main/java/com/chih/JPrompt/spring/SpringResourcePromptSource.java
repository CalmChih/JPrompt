package com.chih.JPrompt.spring;

import com.chih.JPrompt.core.domain.PromptMeta;
import com.chih.JPrompt.core.spi.PromptSource;
import com.chih.JPrompt.core.support.DebouncedFileWatcher;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
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
    
    // 记录加载失败的文件及其异常 (FileName -> Exception)
    private final Map<String, Throwable> loadErrors = new ConcurrentHashMap<>();
    
    private final DebouncedFileWatcher fileWatcher;
    
    // 正则匹配 FrontMatter: 匹配以 --- 开头，中间是 YAML，以 --- 结尾的块
    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$",
            Pattern.DOTALL);
    
    public SpringResourcePromptSource(List<String> locations, long debounceDelayMs, ExecutorService watcherExecutor,
            ScheduledExecutorService debounceExecutor) {
        this.locations = locations;
        
        // 初始化监听器
        // 参数 1: 单个文件变更时 -> 重新加载该文件 (增量更新)
        // 参数 2: 防抖结束后 -> 通知 PromptManager (触发 reload)
        // 将 Spring 管理的线程池传给 Core 组件
        this.fileWatcher = new DebouncedFileWatcher(
                this::reloadSingleFile,
                this::notifyManager,
                debounceDelayMs,
                watcherExecutor,
                debounceExecutor
        );
        
        // 1. 首次全量加载
        initialLoad();
        // 2. 启动监听
        this.fileWatcher.start();
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
    
    private void reloadSingleFile(File file) {
        if (isValidPromptFile(file.getName())) {
            log.info("Incremental reload for file: {}", file.getName());
            loadAndCacheResource(new FileSystemResource(file));
        }
    }
    
    private void notifyManager() {
        if (callback != null) {
            log.info("Applying incremental updates...");
            callback.run();
        }
    }
    
    /**
     * 单个资源加载逻辑 (复用于首次加载和热更新)
     */
    private void loadAndCacheResource(Resource resource) {
        String cacheKey = getResourceCacheKey(resource);
        // 获取资源名称用于错误展示 (更友好的名字)
        String resourceName = getResourceName(resource);
        
        // 资源被删除
        if (!resource.exists()) {
            if (resourceCache.containsKey(cacheKey)) {
                log.info("Resource deleted, removing from cache: {}", resourceName);
                resourceCache.remove(cacheKey);
                // 如果之前有错误，现在文件没了，错误也该消除了
                loadErrors.remove(resourceName);
            }
            return;
        }
        
        try {
            Map<String, PromptMeta> loaded = parseResource(resource);
            
            if (loaded != null && !loaded.isEmpty()) {
                loaded.values().forEach(PromptMeta::validate);
                resourceCache.put(cacheKey, loaded);
                
                // 成功加载，移除之前的错误记录
                loadErrors.remove(resourceName);
                
                // 注册到 Watcher (仅文件)
                if (isFileResource(resource)) {
                    fileWatcher.register(resource.getFile().getParentFile());
                }
            }
        } catch (Exception e) {
            // Failure: Log & Record error, BUT keep stale cache
            log.error("Error loading prompt file: {}. Keeping stale data.", resourceName, e);
            // 记录错误
            loadErrors.put(resourceName, e);
        }
    }
    
    private String getResourceName(Resource resource) {
        try {
            if (isFileResource(resource)) {
                return resource.getFile().getAbsolutePath();
            }
            return resource.getDescription();
        } catch (Exception e) {
            return resource.toString();
        }
    }
    
    private boolean isValidPromptFile(String name) {
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json") || name.endsWith(".md");
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
        
        try (InputStream is = resource.getInputStream()) {
            if (filename.endsWith(".yaml") || filename.endsWith(".yml") || filename.endsWith(".json")) {
                return mapper.readValue(is, new TypeReference<>() {
                });
            } else if (filename.endsWith(".md")) {
                return parseMarkdownStream(is, filename);
            }
        }
        return Collections.emptyMap();
    }
    
    private Map<String, PromptMeta> parseMarkdownStream(InputStream is, String filename) throws IOException {
        String content = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        
        // 稳定性治理：移除 UTF-8 BOM 头 (\uFEFF)
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        
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
            if (filename != null && filename.contains(".")) {
                meta.setId(filename.substring(0, filename.lastIndexOf('.')));
            }
        }
        
        return Collections.singletonMap(meta.getId(), meta);
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
     * 获取当前的错误列表 (供 HealthIndicator 使用)
     */
    public Map<String, Throwable> getLoadErrors() {
        return Collections.unmodifiableMap(loadErrors);
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
        fileWatcher.close(); // 优雅关闭
    }
    
    @Override
    public void close() {
        destroy();
    }
}