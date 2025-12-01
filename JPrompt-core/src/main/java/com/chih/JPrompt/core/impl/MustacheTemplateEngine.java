package com.chih.JPrompt.core.impl;

import com.chih.JPrompt.core.exception.TemplateRenderException;
import com.chih.JPrompt.core.spi.TemplateEngine;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
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
    public Object compile(String template, Function<String, String> partialLoader) {
        if (template == null) {
            return null;
        }
        try {
            // 每次编译创建一个临时的 Factory，绑定当前的 partialLoader
            // 虽然创建 Factory 有开销，但 JPrompt 是预编译模式（只在 reload 时发生），这点开销可忽略
            JPromptMustacheFactory mf = new JPromptMustacheFactory(partialLoader);
            return mf.compile(new StringReader(template), "JPrompt");
        } catch (Exception e) {
            log.error("Failed to compile mustache template", e);
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
        
        public JPromptMustacheFactory(Function<String, String> partialLoader) {
            this.partialLoader = partialLoader;
        }
        
        @Override
        public Reader getReader(String resourceName) {
            // 当 Mustache 解析到 {{> resourceName}} 时会调用此方法
            if (partialLoader != null) {
                String content = partialLoader.apply(resourceName);
                if (content != null) {
                    return new StringReader(content);
                }
            }
            // 如果找不到，返回 null (Mustache 会抛出 TemplateNotFoundException)
            return null;
        }
    }
}