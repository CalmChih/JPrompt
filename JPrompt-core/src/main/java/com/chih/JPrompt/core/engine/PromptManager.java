package com.chih.JPrompt.core.engine;

import com.chih.JPrompt.core.domain.PromptMeta;
import com.chih.JPrompt.core.exception.PromptNotFoundException;
import com.chih.JPrompt.core.impl.MustacheTemplateEngine;
import com.chih.JPrompt.core.impl.NoOpPromptMetrics;
import com.chih.JPrompt.core.spi.CompiledPrompt;
import com.chih.JPrompt.core.spi.PromptChangeEvent;
import com.chih.JPrompt.core.spi.PromptMetrics;
import com.chih.JPrompt.core.spi.PromptSource;
import com.chih.JPrompt.core.spi.TemplateEngine;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
        
        CacheEntry(PromptMeta original, Object compiled) {
            // 内存优化：Entry 瘦身
            // 浅拷贝 Meta，但在 CacheEntry 中置空 template 字符串
            // 因为 compiledTemplate 已经包含了逻辑，运行时不需要 raw string。
            // 这能节省 50%+ 的堆内存。
            this.meta = new PromptMeta();
            this.meta.setId(original.getId());
            this.meta.setModel(original.getModel());
            if (original.getExtensions() != null) {
                original.getExtensions().forEach(this.meta::addExtension);
            }
            this.compiledTemplate = compiled;
        }
    }
    
    // 缓存 Key -> CacheEntry
    private final Cache<String, CacheEntry> cache;
    // 倒排索引：Partial ID -> Set<Parent ID>
    // 记录谁依赖了我。例如: "header" -> ["chat_prompt", "summary_prompt"]
    private final Map<String, Set<String>> reverseDependencies = new ConcurrentHashMap<>();
    
    // 全参构造函数
    public PromptManager(PromptSource source, TemplateEngine templateEngine, PromptMetrics metrics) {
        this.source = source;
        this.templateEngine = templateEngine;
        this.metrics = (metrics != null) ? metrics : new NoOpPromptMetrics();
        
        // 配置 Caffeine
        this.cache = Caffeine.newBuilder()
                // 最大缓存数，防止 OOM
                .maximumSize(10_000)
                .expireAfterAccess(24, TimeUnit.HOURS)
                .recordStats()
                .build();
        
        // 首次加载
        reloadAll();
        
        // 注册增量监听
        this.source.onChange(this::handleIncrementalUpdate);
    }
    
    public PromptManager(PromptSource source, TemplateEngine templateEngine) {
        this(source, templateEngine, new NoOpPromptMetrics());
    }
    
    public PromptManager(PromptSource source) {
        this(source, new MustacheTemplateEngine(), new NoOpPromptMetrics());
    }
    
    /**
     * 增量更新处理逻辑 (线程安全)
     */
    private synchronized void handleIncrementalUpdate(PromptChangeEvent event) {
        // A. 处理删除
        for (String key : event.getRemoved()) {
            cache.invalidate(key);
            removeFromReverseIndex(key);
            log.info("Prompt removed: {}", key);
        }
        
        // B. 处理更新 (自身变化的)
        Set<String> keysToRecompile = new HashSet<>(event.getUpdated().keySet());
        
        // C. 处理级联更新 (依赖变化的)
        // 查找所有依赖了“本次变更 Prompt”的父节点
        for (String changedKey : event.getUpdated().keySet()) {
            Set<String> parents = reverseDependencies.get(changedKey);
            if (parents != null && !parents.isEmpty()) {
                log.info("Cascading update: '{}' changed, impacting parents {}", changedKey, parents);
                keysToRecompile.addAll(parents);
            }
        }
        
        // D. 执行重编译
        if (!keysToRecompile.isEmpty()) {
            recompileBatch(keysToRecompile, event.getUpdated());
        }
    }
    
    private void recompileBatch(Set<String> keys, Map<String, PromptMeta> directUpdates) {
        // 构建 Partial Loader：优先查本次更新的数据，再回源查 Source
        Function<String, String> partialLoader = key -> {
            // 1. 优先：本次变更包里的
            if (directUpdates.containsKey(key)) {
                return directUpdates.get(key).getTemplate();
            }
            // 2. 兜底：去 Source 加载 (因为 Cache 里的 template 可能被我们置空优化掉了)
            PromptMeta meta = source.load(key);
            return meta != null ? meta.getTemplate() : null;
        };
        
        for (String key : keys) {
            try {
                // 获取最新的 Meta (优先从 event 取，没有则从 source 取)
                PromptMeta meta = directUpdates.get(key);
                if (meta == null) {
                    meta = source.load(key);
                }
                
                if (meta == null) {
                    // 可能是级联更新时，父节点也被删除了
                    cache.invalidate(key);
                    continue;
                }
                
                // 编译
                CompiledPrompt compiled = templateEngine.compile(meta.getTemplate(), key, partialLoader);
                
                // 更新缓存
                cache.put(key, new CacheEntry(meta, compiled.getEngineObject()));
                
                // 更新倒排索引 (该 Prompt 依赖了哪些 Child)
                updateReverseIndex(key, compiled.getDependencies());
                
                log.debug("Prompt recompiled: {}", key);
                
            } catch (Exception e) {
                log.error("Failed to recompile prompt: {}", key, e);
                // 策略：编译失败则移除旧缓存，防止数据不一致
                cache.invalidate(key);
            }
        }
    }
    
    /**
     * 更新倒排索引
     * @param parentId 谁依赖了别人
     * @param newDependencies 它依赖了谁
     */
    private void updateReverseIndex(String parentId, Set<String> newDependencies) {
        // 1. 清理旧关系：全量扫描 reverseDependencies (因为没存 forward index，这样做简单点)
        // 生产环境可以再维护一个 forwardDependencies Map<String, Set<String>> 来优化移除性能
        reverseDependencies.values().forEach(parents -> parents.remove(parentId));
        
        // 2. 注册新关系
        if (newDependencies != null) {
            for (String depId : newDependencies) {
                reverseDependencies.computeIfAbsent(depId, k -> ConcurrentHashMap.newKeySet())
                        .add(parentId);
            }
        }
    }
    
    private void removeFromReverseIndex(String key) {
        // 没人能再引用它
        reverseDependencies.remove(key);
        // 它不再引用别人
        reverseDependencies.values().forEach(parents -> parents.remove(key));
    }
    
    private void reloadAll() {
        // 首次全量加载逻辑，复用 recompileBatch
        Map<String, PromptMeta> all = source.loadAll();
        recompileBatch(all.keySet(), all);
        log.info("Initialized {} prompts.", all.size());
    }
    
    public String render(String key, Map<String, Object> variables) {
        CacheEntry entry = cache.getIfPresent(key);
        if (entry == null) {
            // Cache Miss (Caffeine 淘汰了，或者从未加载过)
            // 尝试回源加载 (Lazy Load)
            Map<String, PromptMeta> singleUpdate = new HashMap<>();
            PromptMeta meta = source.load(key);
            if (meta != null) {
                singleUpdate.put(key, meta);
                recompileBatch(Collections.singleton(key), singleUpdate);
                entry = cache.getIfPresent(key);
            }
        }
        
        if (entry == null) {
            throw new PromptNotFoundException(key);
        }
        
        long startTime = System.nanoTime();
        boolean success = false;
        try {
            String result = templateEngine.render(entry.compiledTemplate, variables);
            success = true;
            return result;
        } finally {
            metrics.recordRender(key, System.nanoTime() - startTime, success);
        }
    }
    
    public PromptMeta getMeta(String key) {
        CacheEntry entry = cache.getIfPresent(key);
        return entry != null ? entry.meta : null;
    }
}