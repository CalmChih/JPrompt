package com.chih.JPrompt.core.impl;

import com.chih.JPrompt.core.exception.TemplateRecursionException;
import com.chih.JPrompt.core.exception.TemplateRenderException;
import com.chih.JPrompt.core.spi.CompiledPrompt;
import com.chih.JPrompt.core.spi.TemplateEngine;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 基于 Mustache 的模板引擎实现
 * 支持 {{user.name}}, {{#list}}循环 等高级特性
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
public class MustacheTemplateEngine implements TemplateEngine {
    
    private static final Logger log = LoggerFactory.getLogger(MustacheTemplateEngine.class);
    
    @Override
    public CompiledPrompt compile(String template, String rootId, Function<String, String> partialLoader) {
        if (template == null) {
            return null;
        }
        try {
            // 每次编译创建一个临时的 Factory，绑定当前的 partialLoader
            // 虽然创建 Factory 有开销，但 JPrompt 是预编译模式（只在 reload 时发生），这点开销可忽略
            JPromptMustacheFactory mf = new JPromptMustacheFactory(rootId, partialLoader);
            Mustache mustache = mf.compile(new StringReader(template), rootId);
            // 返回编译对象 + 捕获到的依赖集合
            return new CompiledPrompt(mustache, mf.getRecordedDependencies());
        } catch (Exception e) {
            log.error("Failed to compile mustache template: {}", rootId, e);
            throw e;
        }
    }
    
    @Override
    public String render(Object compiledTemplate, Map<String, Object> variables) {
        if (compiledTemplate == null) {
            return "";
        }
        
        try {
            Mustache mustache = (Mustache) compiledTemplate;
            StringWriter writer = new StringWriter();
            mustache.execute(writer, variables);
            return writer.toString();
        } catch (Exception e) {
            log.error("Template execution failed", e);
            throw new TemplateRenderException("execution_error", e);
        }
    }
    
    /**
     * 自定义 Mustache 工厂，用于支持从 PromptManager 加载子模板
     */
    private static class JPromptMustacheFactory extends DefaultMustacheFactory {
        private final Function<String, String> partialLoader;
        // 用于检测循环引用：记录本次编译链路中涉及的所有 Prompt ID
        private final Set<String> visiting = new HashSet<>();
        // *** 核心：记录编译期遇到的所有引用 ***
        private final Set<String> recordedDependencies = new HashSet<>();
        
        public JPromptMustacheFactory(String rootId, Function<String, String> partialLoader) {
            this.partialLoader = partialLoader;
            // 将根 ID 加入集合
            if (rootId != null) {
                visiting.add(rootId);
            }
        }
        
        @Override
        public Reader getReader(String resourceName) {
            // 1. 循环引用检测
            if (visiting.contains(resourceName)) {
                throw new TemplateRecursionException(
                        String.format("Circular reference detected! Prompt '%s' is referenced recursively.", resourceName)
                );
            }
            
            // 2. *** 记录依赖 ***
            // Mustache 调用此方法说明模板中出现了 {{> resourceName}}
            recordedDependencies.add(resourceName);
            
            // 3. 加载内容
            // 当 Mustache 解析到 {{> resourceName}} 时会调用此方法
            if (partialLoader != null) {
                String content = partialLoader.apply(resourceName);
                if (content != null) {
                    // 3. 标记为已访问 (防止后续再次引用自己)
                    // 注意：这里不需要 remove，因为 DefaultMustacheFactory 会缓存编译结果，
                    // 同一个 Factory 实例内，getReader 对同一个 name 只会调用一次。
                    // 这意味着我们禁止了“在同一个 Prompt 树中多次出现同一个节点”？
                    // 不，Mustache 缓存的是 Template 对象。
                    // 如果 A -> B, A -> C -> B。
                    // 1. A -> getReader(B). OK. Cache B.
                    // 2. A -> getReader(C). OK. Cache C.
                    // 3. Inside C -> {{> B}}. Mustache finds B in cache. getReader(B) is NOT called again.
                    // 所以，我们的 Set 只需要由于检测 A -> B -> A 这种情况。
                    // 如果 getReader 被调用了，说明是第一次遇到。
                    visiting.add(resourceName);
                    
                    return new StringReader(content);
                }
            }
            // 如果找不到，返回 null (Mustache 会抛出 TemplateNotFoundException)
            return null;
        }
        
        public Set<String> getRecordedDependencies() {
            return recordedDependencies;
        }
    }
}