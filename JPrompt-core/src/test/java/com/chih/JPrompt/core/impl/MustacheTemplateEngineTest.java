package com.chih.JPrompt.core.impl;

import com.chih.JPrompt.core.spi.CompiledPrompt;
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

        // 先编译模板，然后渲染
        CompiledPrompt compiled = engine.compile(template, "test-simple", null);
        String result = engine.render(compiled.getEngineObject(), vars);

        assertThat(result).isEqualTo("Hello World");
    }

    @Test
    void testComplexObjectRender() {
        String template = "User: {{user.name}}, Age: {{user.age}}";

        Map<String, Object> user = Map.of("name", "Gemini", "age", 1);
        Map<String, Object> vars = Map.of("user", user);

        // 先编译模板，然后渲染
        CompiledPrompt compiled = engine.compile(template, "test-complex", null);
        String result = engine.render(compiled.getEngineObject(), vars);

        assertThat(result).isEqualTo("User: Gemini, Age: 1");
    }

    @Test
    void testLogicRender() {
        String template = "{{#isAdmin}}Admin{{/isAdmin}}{{^isAdmin}}User{{/isAdmin}}";

        // 先编译模板
        CompiledPrompt compiled = engine.compile(template, "test-logic", null);

        // Case 1: isAdmin = true
        assertThat(engine.render(compiled.getEngineObject(), Map.of("isAdmin", true))).isEqualTo("Admin");

        // Case 2: isAdmin = false
        assertThat(engine.render(compiled.getEngineObject(), Map.of("isAdmin", false))).isEqualTo("User");
    }

    @Test
    void testRenderError() {
        // 测试传入 null 编译模板的情况
        // render 方法对 null 有特殊处理，应该返回空字符串而不是抛出异常
        assertThat(engine.render(null, null)).isEqualTo("");

        // 测试编译错误的情况 - 传入有语法错误的模板
        assertThrows(RuntimeException.class, () -> {
            // Mustache 不完整的循环语法会导致编译异常
            engine.compile("{{#incomplete}}", "test-error", null);
        });
    }

    @Test
    void testCompileWithNullTemplate() {
        // 测试编译 null 模板
        CompiledPrompt result = engine.compile(null, "test-null", null);
        assertThat(result).isNull();
    }

    @Test
    void testCompileWithPartialLoader() {
        String template = "Hello {{> partial}}";

        // 使用 partialLoader
        CompiledPrompt compiled = engine.compile(template, "test-partial", name -> {
            if ("partial".equals(name)) {
                return "World";
            }
            return null;
        });

        String result = engine.render(compiled.getEngineObject(), Map.of());
        assertThat(result).isEqualTo("Hello World");

        // 验证依赖被正确记录
        assertThat(compiled.getDependencies()).contains("partial");
    }
}