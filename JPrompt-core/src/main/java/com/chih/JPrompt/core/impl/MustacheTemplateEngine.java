package com.chih.JPrompt.core.impl;

import com.chih.JPrompt.core.exception.TemplateRenderException;
import com.chih.JPrompt.core.spi.TemplateEngine;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

/**
 * 基于 Mustache 的模板引擎实现
 * 支持 {{user.name}}, {{#list}}循环 等高级特性
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
public class MustacheTemplateEngine implements TemplateEngine {
    
    private static final Logger log = LoggerFactory.getLogger(MustacheTemplateEngine.class);
    private final MustacheFactory mf = new DefaultMustacheFactory();
    
    @Override
    public Object compile(String template) {
        if (template == null) {
            return null;
        }
        try {
            // "JPrompt" 是模板名称，用于报错堆栈标识
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
}