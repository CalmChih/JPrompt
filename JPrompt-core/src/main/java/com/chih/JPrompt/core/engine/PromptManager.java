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
import java.util.function.Function;

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
            
            // 构建 Loader：优先从 newData 找，找不到去 oldCache 找 (容错)
            final Map<String, PromptMeta> finalNewData = newData;
            Function<String, String> partialLoader = key -> {
                PromptMeta meta = finalNewData.get(key);
                if (meta != null) {
                    return meta.getTemplate();
                }
                // loadAll 返回的是全量合并数据，所以 newData 应该是全的。
                // 这里只查 newData 即可。
                return null;
            };
            
            for (Map.Entry<String, PromptMeta> entry : newData.entrySet()) {
                String key = entry.getKey();
                PromptMeta newMeta = entry.getValue();
                
                CacheEntry oldEntry = oldCache.get(key);
                Object compiledObject;
                
                // 智能复用检测：
                // 1. 自身内容没变
                // 2. 这是一个复杂点：如果它引用的子模板变了，它也得重编译！
                // 目前的 Objects.equals(old, new) 无法检测子模板变化。
                // 稳妥起见：为了支持 Partials 热更新，我们需要牺牲一点性能，
                // 或者在这里做更复杂的依赖图分析。
                //
                // >>> 决策：MVP 阶段，为了确保子模板更新生效，暂时取消“智能复用”，
                // >>> 或者仅当通过 Mustache 能够确定无依赖时才复用。
                // >>> 简单方案：全量重编译。因为 JPrompt 是预编译，reload 是异步的，
                // >>> 几百个 Prompt 重编译也就几十毫秒，完全可接受。
                
                // 重新编译 (传入 partialLoader)
                compiledObject = templateEngine.compile(newMeta.getTemplate(), partialLoader);
                
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