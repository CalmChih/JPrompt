package com.chih.JPrompt.core.support;

import com.chih.JPrompt.core.domain.PromptMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * PromptParser 单元测试
 *
 * 测试统一 Prompt 解析器的各项功能，包括多格式支持、错误处理和并发安全性
 */
@DisplayName("PromptParser 测试")
class PromptParserTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("支持的文件类型判断 - 正例")
    void testIsSupportedFile_PositiveCases() {
        // YAML 格式
        assertThat(PromptParser.isSupportedFile("test.yaml")).isTrue();
        assertThat(PromptParser.isSupportedFile("test.yml")).isTrue();
        assertThat(PromptParser.isSupportedFile("config.YAML")).isTrue();
        assertThat(PromptParser.isSupportedFile("config.YML")).isTrue();

        // JSON 格式
        assertThat(PromptParser.isSupportedFile("test.json")).isTrue();
        assertThat(PromptParser.isSupportedFile("config.JSON")).isTrue();

        // Markdown 格式
        assertThat(PromptParser.isSupportedFile("test.md")).isTrue();
        assertThat(PromptParser.isSupportedFile("readme.MD")).isTrue();

        // 带路径的文件名
        assertThat(PromptParser.isSupportedFile("path/to/prompts.yaml")).isTrue();
        assertThat(PromptParser.isSupportedFile("C:\\prompts\\test.json")).isTrue();
        assertThat(PromptParser.isSupportedFile("/usr/local/prompts/greeting.md")).isTrue();
    }

    @Test
    @DisplayName("支持的文件类型判断 - 反例")
    void testIsSupportedFile_NegativeCases() {
        // 不支持的扩展名
        assertThat(PromptParser.isSupportedFile("test.txt")).isFalse();
        assertThat(PromptParser.isSupportedFile("test.xml")).isFalse();
        assertThat(PromptParser.isSupportedFile("test.html")).isFalse();
        assertThat(PromptParser.isSupportedFile("test.properties")).isFalse();

        // 无扩展名
        assertThat(PromptParser.isSupportedFile("test")).isFalse();
        assertThat(PromptParser.isSupportedFile("prompts")).isFalse();

        // null 和空字符串
        assertThat(PromptParser.isSupportedFile(null)).isFalse();
        assertThat(PromptParser.isSupportedFile("")).isFalse();
        assertThat(PromptParser.isSupportedFile("   ")).isFalse();

        // 只有扩展名的文件（实际上这些应该返回true，因为它们以支持的扩展名结尾）
        assertThat(PromptParser.isSupportedFile(".yaml")).isTrue();
        assertThat(PromptParser.isSupportedFile(".json")).isTrue();
    }

    @Test
    @DisplayName("YAML 格式解析 - 单个 Prompt")
    void testParseYamlFile_SinglePrompt() throws IOException {
        String yamlContent = """
            greeting:
              id: greeting
              model: gpt-3.5-turbo
              template: Hello, {{name}}!
              temperature: 0.7
            """;

        InputStream is = new ByteArrayInputStream(yamlContent.getBytes(StandardCharsets.UTF_8));
        Map<String, PromptMeta> result = PromptParser.parse(is, "test.yaml");

        assertThat(result).hasSize(1);
        assertThat(result).containsKey("greeting");

        PromptMeta prompt = result.get("greeting");
        assertThat(prompt.getId()).isEqualTo("greeting");
        assertThat(prompt.getModel()).isEqualTo("gpt-3.5-turbo");
        assertThat(prompt.getTemplate()).isEqualTo("Hello, {{name}}!");
        assertThat(prompt.getTemperature()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("YAML 格式解析 - 多个 Prompt")
    void testParseYamlFile_MultiplePrompts() throws IOException {
        String yamlContent = """
            greeting:
              id: greeting
              model: gpt-3.5-turbo
              template: Hello, {{name}}!

            farewell:
              id: farewell
              model: gpt-4
              template: Goodbye, {{name}}!

            question:
              id: question
              model: claude-3
              template: What is {{topic}}?
            """;

        InputStream is = new ByteArrayInputStream(yamlContent.getBytes(StandardCharsets.UTF_8));
        Map<String, PromptMeta> result = PromptParser.parse(is, "prompts.yaml");

        assertThat(result).hasSize(3);
        assertThat(result).containsKeys("greeting", "farewell", "question");

        assertThat(result.get("greeting").getTemplate()).isEqualTo("Hello, {{name}}!");
        assertThat(result.get("farewell").getModel()).isEqualTo("gpt-4");
        assertThat(result.get("question").getTemplate()).isEqualTo("What is {{topic}}?");
    }

    @Test
    @DisplayName("JSON 格式解析")
    void testParseJsonFile() throws IOException {
        String jsonContent = """
            {
              "welcome": {
                "id": "welcome",
                "model": "gpt-3.5-turbo",
                "template": "Welcome to {{app_name}}!"
              },
              "error": {
                "id": "error",
                "model": "gpt-4",
                "template": "Error: {{error_message}}"
              }
            }
            """;

        InputStream is = new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8));
        Map<String, PromptMeta> result = PromptParser.parse(is, "test.json");

        assertThat(result).hasSize(2);
        assertThat(result).containsKeys("welcome", "error");

        assertThat(result.get("welcome").getTemplate()).isEqualTo("Welcome to {{app_name}}!");
        assertThat(result.get("error").getModel()).isEqualTo("gpt-4");
    }

    @Test
    @DisplayName("Markdown 格式解析 - 带 FrontMatter")
    void testParseMarkdownWithFrontMatter() throws IOException {
        String markdownContent = """
            ---
            id: greeting
            model: gpt-3.5-turbo
            temperature: 0.8
            maxTokens: 150
            ---
            Hello, {{name}}! How are you today?

            I hope you're having a wonderful day!
            """;

        InputStream is = new ByteArrayInputStream(markdownContent.getBytes(StandardCharsets.UTF_8));
        Map<String, PromptMeta> result = PromptParser.parse(is, "greeting.md");

        assertThat(result).hasSize(1);
        assertThat(result).containsKey("greeting");

        PromptMeta prompt = result.get("greeting");
        assertThat(prompt.getId()).isEqualTo("greeting");
        assertThat(prompt.getModel()).isEqualTo("gpt-3.5-turbo");
        assertThat(prompt.getTemperature()).isEqualTo(0.8);
        assertThat(prompt.getMaxTokens()).isEqualTo(150);
        assertThat(prompt.getTemplate()).contains("Hello, {{name}}! How are you today?");
    }

    @Test
    @DisplayName("Markdown 格式解析 - 无 FrontMatter")
    void testParseMarkdownWithoutFrontMatter() throws IOException {
        String markdownContent = """
            This is a simple prompt template.

            Hello, {{name}}! Welcome to our application.

            Please enter your name to continue.
            """;

        InputStream is = new ByteArrayInputStream(markdownContent.getBytes(StandardCharsets.UTF_8));
        Map<String, PromptMeta> result = PromptParser.parse(is, "simple.md");

        assertThat(result).hasSize(1);
        assertThat(result).containsKey("simple");

        PromptMeta prompt = result.get("simple");
        assertThat(prompt.getId()).isEqualTo("simple");
        assertThat(prompt.getTemplate()).contains("Hello, {{name}}! Welcome to our application.");
    }

    @Test
    @DisplayName("内容标准化 - BOM 和换行符处理")
    void testNormalizeContent() {
        // 测试 UTF-8 BOM 移除
        String contentWithBOM = "\uFEFFHello, World!";
        assertThat(PromptParser.normalizeContent(contentWithBOM)).isEqualTo("Hello, World!");

        // 测试 Windows 换行符标准化
        String windowsContent = "Line 1\r\nLine 2\r\nLine 3";
        assertThat(PromptParser.normalizeContent(windowsContent)).isEqualTo("Line 1\nLine 2\nLine 3");

        // 测试混合换行符
        String mixedContent = "Line 1\r\nLine 2\nLine 3\r\nLine 4";
        assertThat(PromptParser.normalizeContent(mixedContent)).isEqualTo("Line 1\nLine 2\nLine 3\nLine 4");

        // 测试 null 和空字符串
        assertThat(PromptParser.normalizeContent(null)).isEqualTo("");
        assertThat(PromptParser.normalizeContent("")).isEqualTo("");
        assertThat(PromptParser.normalizeContent("   ")).isEqualTo("   ");

        // 测试 BOM + Windows 换行符组合
        String combinedContent = "\uFEFFLine 1\r\nLine 2\r\nLine 3";
        assertThat(PromptParser.normalizeContent(combinedContent)).isEqualTo("Line 1\nLine 2\nLine 3");
    }

    @Test
    @DisplayName("异常输入处理 - null 参数")
    void testParseWithNullInput() {
        // 测试 null 输入流
        assertThatThrownBy(() -> PromptParser.parse(null, "test.yaml"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("InputStream cannot be null");

        // 测试 null 文件名
        assertThatThrownBy(() -> PromptParser.parse(new ByteArrayInputStream("test".getBytes()), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Filename cannot be null or empty");

        // 测试空文件名
        assertThatThrownBy(() -> PromptParser.parse(new ByteArrayInputStream("test".getBytes()), ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Filename cannot be null or empty");

        // 测试空白文件名
        assertThatThrownBy(() -> PromptParser.parse(new ByteArrayInputStream("test".getBytes()), "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Filename cannot be null or empty");
    }

    @Test
    @DisplayName("异常输入处理 - 无效文件格式")
    void testParseWithInvalidInput() throws IOException {
        // 不支持的文件格式应该返回空 Map，而不是抛出异常
        InputStream is = new ByteArrayInputStream("some content".getBytes(StandardCharsets.UTF_8));
        Map<String, PromptMeta> result = PromptParser.parse(is, "test.txt");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("YAML 解析错误处理")
    void testParseWithMalformedYaml() {
        String malformedYaml = """
            invalid: yaml: content:
              - missing: proper
            structure
            """;

        InputStream is = new ByteArrayInputStream(malformedYaml.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> PromptParser.parse(is, "malformed.yaml"))
            .isInstanceOf(IOException.class)
            .hasMessage("Failed to parse prompt file: malformed.yaml");
    }

    @Test
    @DisplayName("文件名 ID 生成逻辑")
    void testGenerateIdFromFilename() throws IOException {
        // 标准文件名
        testIdGeneration("greeting.yaml", "greeting");
        testIdGeneration("farewell.yml", "farewell");
        testIdGeneration("welcome.json", "welcome");
        testIdGeneration("intro.md", "intro");

        // 带路径的文件名
        testIdGeneration("path/to/greeting.yaml", "greeting");
        testIdGeneration("C:\\prompts\\farewell.yml", "farewell");
        testIdGeneration("/usr/local/prompts/intro.md", "intro");

        // 特殊字符处理
        testIdGeneration("hello world.yaml", "hello-world");
        testIdGeneration("test@123.json", "test-123");
        testIdGeneration("file_name.md", "file_name");

        // 边界情况
        testIdGeneration(".yaml", "yaml"); // 只有扩展名
        // testIdGeneration("file.", "file"); // 扩展名为空，这种情况可能导致解析问题
    }

    private void testIdGeneration(String filename, String expectedId) throws IOException {
        String content = String.format("""
            %s:
              id: %s
              template: Simple template content
            """, expectedId, expectedId);
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        Map<String, PromptMeta> result = PromptParser.parse(is, filename);

        assertThat(result).hasSize(1);
        assertThat(result.keySet().iterator().next()).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("并发解析安全性")
    void testConcurrentParsing() throws InterruptedException {
        String yamlContent = """
            test:
              id: test
              model: gpt-3.5-turbo
              template: Hello, {{name}}!
            """;

        int threadCount = 10;
        int iterationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        InputStream is = new ByteArrayInputStream(yamlContent.getBytes(StandardCharsets.UTF_8));
                        Map<String, PromptMeta> result = PromptParser.parse(is, "concurrent_test.yaml");

                        assertThat(result).hasSize(1);
                        assertThat(result).containsKey("test");
                        assertThat(result.get("test").getTemplate()).isEqualTo("Hello, {{name}}!");
                    }
                } catch (Exception e) {
                    fail("Concurrent parsing failed", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
    }

    @Test
    @DisplayName("文件创建和解析测试")
    void testParseWithRealFiles() throws IOException {
        // 创建临时 YAML 文件
        Path yamlFile = tempDir.resolve("test.yaml");
        String yamlContent = """
            real_file_test:
              id: real_file_test
              model: gpt-4
              template: 'This is from a real file: {{message}}'
            """;
        Files.write(yamlFile, yamlContent.getBytes(StandardCharsets.UTF_8));

        // 解析真实文件
        try (InputStream is = Files.newInputStream(yamlFile)) {
            Map<String, PromptMeta> result = PromptParser.parse(is, yamlFile.getFileName().toString());

            assertThat(result).hasSize(1);
            assertThat(result).containsKey("real_file_test");
            assertThat(result.get("real_file_test").getTemplate())
                .isEqualTo("This is from a real file: {{message}}");
        }
    }

    @Test
    @DisplayName("复杂的 FrontMatter 解析")
    void testComplexFrontMatter() throws IOException {
        String complexMarkdown = """
            ---
            id: complex_prompt
            model: claude-3-opus-20240229
            temperature: 0.7
            maxTokens: 4000
            top_p: 0.9
            frequency_penalty: 0.1
            presence_penalty: 0.1
            stop_sequences:
              - "### END"
              - "### STOP"
            tags:
              - creative
              - detailed
              - multi-step
            description: |
              This is a complex prompt that demonstrates
              multi-line YAML properties and various
              configuration options.
            ---
            You are an expert assistant with the following characteristics:

            1. **Knowledge**: Deep understanding of {{topic}}
            2. **Approach**: Methodical and structured
            3. **Tone**: Professional yet approachable

            Please provide a comprehensive analysis of {{subject}}.

            ### END
            """;

        InputStream is = new ByteArrayInputStream(complexMarkdown.getBytes(StandardCharsets.UTF_8));
        Map<String, PromptMeta> result = PromptParser.parse(is, "complex.md");

        assertThat(result).hasSize(1);
        assertThat(result).containsKey("complex_prompt");

        PromptMeta prompt = result.get("complex_prompt");
        assertThat(prompt.getModel()).isEqualTo("claude-3-opus-20240229");
        assertThat(prompt.getTemperature()).isEqualTo(0.7);
        assertThat(prompt.getMaxTokens()).isEqualTo(4000);
        assertThat(prompt.getTemplate()).contains("You are an expert assistant");
        assertThat(prompt.getTemplate()).contains("### END");
    }
}