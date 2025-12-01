package com.chih.JPrompt.core.impl;

import com.chih.JPrompt.core.domain.PromptMeta;
import com.chih.JPrompt.core.spi.PromptSource;
import com.chih.JPrompt.core.support.DebouncedFileWatcher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于文件的 Prompt 源实现
 * <p>
 * 策略：
 * 1. 优先读取 Jar 包外部文件 (支持热更新)
 * 2. 外部文件不存在时，读取 Classpath 内部文件 (保底)
 * </p>
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
public class FilePromptSource implements PromptSource {
    
    private static final Logger log = LoggerFactory.getLogger(FilePromptSource.class);
    
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    // 支持多个路径 (可能是文件，也可能是目录)
    private final List<String> configPaths;
    private volatile Runnable changeCallback;
    
    // Key: 文件绝对路径 (或 classpath 标识), Value: 该文件包含的所有 Prompt
    private final Map<String, Map<String, PromptMeta>> resourceCache = new ConcurrentHashMap<>();
    
    private final DebouncedFileWatcher fileWatcher;
    // 默认防抖时间 500ms
    private static final long DEBOUNCE_DELAY_MS = 500;
    
    // 正则匹配 FrontMatter
    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL);
    
    /**
     * 构造函数 (支持传入多个路径)
     * @param paths 文件路径或目录路径列表
     */
    public FilePromptSource(String... paths) {
        this(Arrays.asList(paths));
    }
    
    public FilePromptSource(List<String> paths) {
        this.configPaths = new ArrayList<>(paths);
        
        // 初始化监听器
        this.fileWatcher = new DebouncedFileWatcher(
                this::reloadSingleFile,
                this::notifyManager,
                DEBOUNCE_DELAY_MS
        );
        
        // 首次加载 & 注册监听
        initialLoadAndWatch();
        
        // 启动监听线程
        this.fileWatcher.start();
    }
    
    /**
     * 遍历配置路径，加载文件并注册监听
     */
    private void initialLoadAndWatch() {
        for (String pathStr : configPaths) {
            File file = new File(pathStr);
            
            // 1. 如果是文件系统存在的路径 (File/Dir)
            if (file.exists()) {
                if (file.isDirectory()) {
                    // 情况 A: 目录 -> 扫描目录下所有文件 + 监听目录
                    loadDirectory(file);
                    fileWatcher.register(file);
                } else {
                    // 情况 B: 单文件 -> 加载文件 + 监听父目录
                    reloadSingleFile(file);
                    fileWatcher.register(file.getParentFile());
                }
            }
            // 2. 如果文件系统不存在，尝试作为 Classpath 资源加载 (只读)
            else {
                loadFromClasspath(pathStr);
            }
        }
    }
    
    /**
     * 加载目录下的所有支持文件
     */
    private void loadDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                // 递归加载子目录 (可选，视需求而定。这里简单实现递归)
                loadDirectory(file);
                fileWatcher.register(file);
            } else {
                if (isSupportedFile(file.getName())) {
                    reloadSingleFile(file);
                }
            }
        }
    }
    
    /**
     * 增量更新：重新加载单个文件并更新缓存
     */
    private void reloadSingleFile(File file) {
        if (!isSupportedFile(file.getName())) {
            return;
        }
        
        try {
            log.info("Incremental reload for file: {}", file.getAbsolutePath());
            Map<String, PromptMeta> loaded = parseFile(file);
            
            if (loaded != null) {
                // 校验
                loaded.values().forEach(PromptMeta::validate);
                // 更新缓存
                resourceCache.put(file.getAbsolutePath(), loaded);
            }
        } catch (Exception e) {
            log.error("Failed to reload file: {}", file.getAbsolutePath(), e);
            // 注意：不清除旧缓存，保持服务可用性
        }
    }
    
    /**
     * 通知上层 Manager 数据已更新
     */
    private void notifyManager() {
        if (changeCallback != null) {
            log.info("Applying incremental updates...");
            changeCallback.run();
        }
    }
    
    private boolean isSupportedFile(String name) {
        return name.endsWith(".yaml") || name.endsWith(".yml") ||
                name.endsWith(".json") || name.endsWith(".md");
    }
    
    private void loadFromClasspath(String path) {
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                log.info("Loading classpath resource: {}", path);
                Map<String, PromptMeta> loaded = parseStream(is, path);
                if (loaded != null && !loaded.isEmpty()) {
                    loaded.values().forEach(PromptMeta::validate);
                    resourceCache.put("classpath:" + path, loaded);
                }
            } else {
                // 只有在既不是文件也不是 classpath 时才警告
                log.warn("Prompt path not found (checked File System and Classpath): {}", path);
            }
        } catch (Exception e) {
            log.error("Failed to load classpath prompt: {}", path, e);
        }
    }
    
    /**
     * 获取所有 Prompts (直接合并内存缓存，速度极快)
     */
    @Override
    public Map<String, PromptMeta> loadAll() {
        Map<String, PromptMeta> allPrompts = new HashMap<>();
        resourceCache.values().forEach(allPrompts::putAll);
        return allPrompts;
    }
    
    private Map<String, PromptMeta> parseFile(File file) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            return parseStream(is, file.getName());
        }
    }
    
    private Map<String, PromptMeta> parseStream(InputStream is, String filename) throws IOException {
        if (filename.endsWith(".yaml") || filename.endsWith(".yml") || filename.endsWith(".json")) {
            return mapper.readValue(is, new TypeReference<>() {});
        } else if (filename.endsWith(".md")) {
            return parseMarkdown(is, filename);
        }
        return Collections.emptyMap();
    }
    
    /**
     * Markdown 解析实现 (Core 模块纯 Java 版)
     */
    private Map<String, PromptMeta> parseMarkdown(InputStream is, String filename) throws IOException {
        // 读取流内容
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        String content = buffer.toString(StandardCharsets.UTF_8);
        
        // 统一换行符
        content = content.replace("\r\n", "\n");
        
        PromptMeta meta = new PromptMeta();
        String templateBody = content;
        
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(content);
        if (matcher.find()) {
            String yamlPart = matcher.group(1);
            templateBody = matcher.group(2).trim();
            if (yamlPart != null && !yamlPart.isEmpty()) {
                meta = mapper.readValue(yamlPart, PromptMeta.class);
            }
        }
        
        meta.setTemplate(templateBody);
        
        // ID 兜底
        if (meta.getId() == null || meta.getId().isEmpty()) {
            if (filename.contains(".")) {
                meta.setId(filename.substring(0, filename.lastIndexOf('.')));
            } else {
                meta.setId(filename);
            }
        }
        
        // 校验
        meta.validate();
        
        return Collections.singletonMap(meta.getId(), meta);
    }
    
    @Override
    public void onChange(Runnable callback) {
        this.changeCallback = callback;
    }
    
    @Override
    public void close() {
        fileWatcher.close();
    }
}