package com.chih.JPrompt.core.integration;

import com.chih.JPrompt.core.domain.PromptMeta;
import com.chih.JPrompt.core.impl.FilePromptSource;
import com.chih.JPrompt.core.support.PromptParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 重构验证测试
 *
 * 验证重构后的组件功能正常
 */
class RefactoringValidationTest {

    @Test
    void testPromptParserFunctionality() throws IOException {
        // 测试 YAML 解析
        String yamlContent = """
                test-prompt:
                  id: test-prompt
                  model: gpt-3.5-turbo
                  temperature: 0.7
                  template: Hello, {{name}}!
                """;

        Map<String, PromptMeta> result = PromptParser.parse(
            new ByteArrayInputStream(yamlContent.getBytes()), "test.yaml");

        assertThat(result).hasSize(1);
        PromptMeta prompt = result.get("test-prompt");
        assertThat(prompt).isNotNull();
        assertThat(prompt.getId()).isEqualTo("test-prompt");
        assertThat(prompt.getTemplate()).isEqualTo("Hello, {{name}}!");
    }

    @Test
    void testFilePromptSourceBasicFunctionality(@TempDir Path tempDir) throws IOException {
        // 创建测试文件
        Path yamlFile = tempDir.resolve("test.yaml");
        String yamlContent = """
                greeting:
                  id: greeting
                  model: gpt-3.5-turbo
                  temperature: 0.7
                  template: Hello, {{name}}!
                """;
        Files.write(yamlFile, yamlContent.getBytes());

        // 创建 FilePromptSource
        FilePromptSource source = new FilePromptSource(yamlFile.toString());

        // 测试 loadAll
        Map<String, PromptMeta> allPrompts = source.loadAll();
        assertThat(allPrompts).hasSize(1);
        assertThat(allPrompts).containsKey("greeting");

        // 测试 load (单个查询)
        PromptMeta greeting = source.load("greeting");
        assertThat(greeting).isNotNull();
        assertThat(greeting.getId()).isEqualTo("greeting");
        assertThat(greeting.getTemplate()).isEqualTo("Hello, {{name}}!");

        // 清理资源
        try {
            source.close();
        } catch (Exception e) {
            // 忽略关闭异常
        }
    }

    @Test
    void testFileSupport() {
        // 验证文件类型判断
        assertThat(PromptParser.isSupportedFile("test.yaml")).isTrue();
        assertThat(PromptParser.isSupportedFile("test.yml")).isTrue();
        assertThat(PromptParser.isSupportedFile("test.json")).isTrue();
        assertThat(PromptParser.isSupportedFile("test.md")).isTrue();
        assertThat(PromptParser.isSupportedFile("test.txt")).isFalse();
    }
}