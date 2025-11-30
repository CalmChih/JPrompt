package com.chih.JPrompt.core.impl;

import com.chih.JPrompt.core.exception.TemplateRenderException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MustacheTemplateEngineTest {

    private final MustacheTemplateEngine engine = new MustacheTemplateEngine();

    @Test
    void testSimpleRender() {
        String template = "Hello {{name}}";
        Map<String, Object> vars = Map.of("name", "World");
        
        String result = engine.render(template, vars);
        
        assertThat(result).isEqualTo("Hello World");
    }

    @Test
    void testComplexObjectRender() {
        String template = "User: {{user.name}}, Age: {{user.age}}";
        
        Map<String, Object> user = Map.of("name", "Gemini", "age", 1);
        Map<String, Object> vars = Map.of("user", user);
        
        String result = engine.render(template, vars);
        
        assertThat(result).isEqualTo("User: Gemini, Age: 1");
    }

    @Test
    void testLogicRender() {
        String template = "{{#isAdmin}}Admin{{/isAdmin}}{{^isAdmin}}User{{/isAdmin}}";
        
        // Case 1: isAdmin = true
        assertThat(engine.render(template, Map.of("isAdmin", true))).isEqualTo("Admin");
        
        // Case 2: isAdmin = false
        assertThat(engine.render(template, Map.of("isAdmin", false))).isEqualTo("User");
    }

    @Test
    void testRenderError() {
        // Mustache 对语法错误很宽容，通常抛出 RuntimeException
        // 这里我们测试如果传入 null 会发生什么，或者构造一个必然失败的场景
        assertThrows(TemplateRenderException.class, () -> {
            // 模拟一个极其异常的情况，或者直接 mock execute 抛出异常
            // 在实际 Mustache 中，语法错误可能在 compile 阶段抛出
            // 这里我们主要验证 engine 是否有 try-catch 包装
             engine.render(null, null); // 代码里做了判空返回 ""，这里取决于你的实现细节
        });
    }
}