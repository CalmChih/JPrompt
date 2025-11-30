package com.chih.JPrompt.core.engine;

import com.chih.JPrompt.core.domain.PromptMeta;
import com.chih.JPrompt.core.exception.PromptNotFoundException;
import com.chih.JPrompt.core.impl.MustacheTemplateEngine;
import com.chih.JPrompt.core.impl.NoOpPromptMetrics;
import com.chih.JPrompt.core.spi.PromptMetrics;
import com.chih.JPrompt.core.spi.PromptSource;
import com.chih.JPrompt.core.spi.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 核心管理器：负责协调 Source 和 Cache
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
public class PromptManager {
    
    private static final Logger log = LoggerFactory.getLogger(PromptManager.class);
    
    private final PromptSource source;
    private final TemplateEngine templateEngine;
    private final PromptMetrics metrics;
    
    // 内部类：缓存条目，同时持有元数据和编译好的对象
    private static class CacheEntry {
        final PromptMeta meta;
        final Object compiledTemplate;
        
        CacheEntry(PromptMeta meta, Object compiledTemplate) {
            this.meta = meta;
            this.compiledTemplate = compiledTemplate;
        }
    }
    
    // 缓存 Key -> CacheEntry
    private volatile Map<String, CacheEntry> cache = Collections.emptyMap();
    
    // 全参构造函数
    public PromptManager(PromptSource source, TemplateEngine templateEngine, PromptMetrics metrics) {
        this.source = source;
        this.templateEngine = templateEngine;
        this.metrics = (metrics != null) ? metrics : new NoOpPromptMetrics();
        
        // 首次加载
        reload(true);
        
        // 注册监听
        this.source.onChange(() -> reload(false));
    }
    
    public PromptManager(PromptSource source, TemplateEngine templateEngine) {
        this(source, templateEngine, new NoOpPromptMetrics());
    }
    
    public PromptManager(PromptSource source) {
        this(source, new MustacheTemplateEngine(), new NoOpPromptMetrics());
    }
    
    /**
     * 线程安全的重载方法 (Copy-On-Write)
     */
    private synchronized void reload(boolean isInitialLoad) {
        try {
            Map<String, PromptMeta> newData = source.loadAll();
            if (newData == null) {
                newData = Collections.emptyMap();
            }
            
            // 构建新缓存
            Map<String, CacheEntry> newCache = new HashMap<>(newData.size());
            // 获取旧缓存引用，用于对比复用
            Map<String, CacheEntry> oldCache = this.cache;
            
            for (Map.Entry<String, PromptMeta> entry : newData.entrySet()) {
                String key = entry.getKey();
                PromptMeta newMeta = entry.getValue();
                
                // === 核心优化：智能复用逻辑 ===
                CacheEntry oldEntry = oldCache.get(key);
                Object compiledObject;
                
                // 如果旧缓存存在，且模板内容未发生变化，则直接复用旧的编译对象
                if (oldEntry != null && Objects.equals(oldEntry.meta.getTemplate(), newMeta.getTemplate())) {
                    compiledObject = oldEntry.compiledTemplate;
                } else {
                    // 内容变了，或者新 Key，重新编译
                    compiledObject = templateEngine.compile(newMeta.getTemplate());
                }
                
                newCache.put(key, new CacheEntry(newMeta, compiledObject));
            }
            
            this.cache = newCache;
            log.info("Prompts reloaded. Cache size: {}", newCache.size());
            
        } catch (Exception e) {
            log.error("Failed to reload prompts", e);
            if (isInitialLoad) {
                throw new IllegalStateException("Failed to load prompts during startup", e);
            }
        }
    }
    
    public String render(String key, Map<String, Object> variables) {
        long startTime = System.nanoTime();
        boolean success = false;
        try {
            // 直接获取 Entry，一步到位
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                throw new PromptNotFoundException(key);
            }
            
            // 直接执行，无需 Map 查找，无需 Hash 计算
            String result = templateEngine.render(entry.compiledTemplate, variables);
            success = true;
            return result;
        } finally {
            metrics.recordRender(key, System.nanoTime() - startTime, success);
        }
    }
    
    public PromptMeta getMeta(String key) {
        CacheEntry entry = cache.get(key);
        return entry != null ? entry.meta : null;
    }
}