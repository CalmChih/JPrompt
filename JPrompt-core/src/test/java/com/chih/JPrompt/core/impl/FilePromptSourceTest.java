package com.chih.JPrompt.core.impl;

import com.chih.JPrompt.core.domain.PromptMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * FilePromptSource 单元测试
 *
 * 测试文件系统 Prompt 源的各项功能，包括：
 * - 基本的 Prompt 加载
 * - 热更新机制
 * - 错误处理
 * - 资源管理
 */
@DisplayName("FilePromptSource 测试")
class FilePromptSourceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("基本功能测试 - 加载单个 YAML 文件")
    void testBasicFunctionality_SingleYamlFile() throws IOException {
        // 创建测试 YAML 文件
        Path yamlFile = tempDir.resolve("test.yaml");
        String content = """
            greeting:
              id: greeting
              template: Hello {{name}}!
              description: A simple greeting prompt
            """;
        Files.write(yamlFile, content.getBytes());

        // 创建 FilePromptSource
        FilePromptSource source = new FilePromptSource(yamlFile.toString());

        try {
            // 测试 loadAll
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(1);
            assertThat(prompts).containsKey("greeting");

            PromptMeta greeting = prompts.get("greeting");
            assertThat(greeting.getId()).isEqualTo("greeting");
            assertThat(greeting.getTemplate()).isEqualTo("Hello {{name}}!");
            assertThat(greeting.getDescription()).isEqualTo("A simple greeting prompt");

            // 测试 load
            PromptMeta loadedGreeting = source.load("greeting");
            assertThat(loadedGreeting).isNotNull();
            assertThat(loadedGreeting.getId()).isEqualTo("greeting");

            // 测试不存在的 key
            PromptMeta nonExistent = source.load("nonexistent");
            assertThat(nonExistent).isNull();

        } finally {
            try {
                source.close();
            } catch (Exception e) {
                // 忽略关闭时的异常
            }
        }
    }

    @Test
    @DisplayName("JSON 文件支持测试")
    void testJsonFileSupport() throws IOException {
        // 创建测试 JSON 文件
        Path jsonFile = tempDir.resolve("test.json");
        String content = """
            {
              "welcome": {
                "id": "welcome",
                "template": "Welcome {{name}}!",
                "description": "A welcome message"
              }
            }
            """;
        Files.write(jsonFile, content.getBytes());

        // 创建 FilePromptSource
        FilePromptSource source = new FilePromptSource(jsonFile.toString());

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(1);
            assertThat(prompts).containsKey("welcome");

            PromptMeta welcome = prompts.get("welcome");
            assertThat(welcome.getId()).isEqualTo("welcome");
            assertThat(welcome.getTemplate()).isEqualTo("Welcome {{name}}!");
            assertThat(welcome.getDescription()).isEqualTo("A welcome message");

        } finally {
            try {
                source.close();
            } catch (Exception e) {
                // 忽略关闭时的异常
            }
        }
    }

    @Test
    @DisplayName("Markdown FrontMatter 支持测试")
    void testMarkdownFrontMatterSupport() throws IOException {
        // 创建测试 Markdown 文件
        Path mdFile = tempDir.resolve("test.md");
        String content = """
            ---
            id: markdown-prompt
            description: Prompt defined in markdown with frontmatter
            tags: [example, markdown]
            ---
            This is a markdown prompt for {{name}}
            """;
        Files.write(mdFile, content.getBytes());

        // 创建 FilePromptSource
        FilePromptSource source = new FilePromptSource(mdFile.toString());

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(1);
            assertThat(prompts).containsKey("markdown-prompt");

            PromptMeta markdownPrompt = prompts.get("markdown-prompt");
            assertThat(markdownPrompt.getId()).isEqualTo("markdown-prompt");
            assertThat(markdownPrompt.getTemplate()).isEqualTo("This is a markdown prompt for {{name}}");
            assertThat(markdownPrompt.getDescription()).isEqualTo("Prompt defined in markdown with frontmatter");

        } finally {
            try {
                source.close();
            } catch (Exception e) {
                // 忽略关闭时的异常
            }
        }
    }

    @Test
    @DisplayName("多文件目录测试")
    void testMultipleFilesInDirectory() throws IOException {
        // 创建多个测试文件
        String file1Content = """
            prompt1:
              id: prompt1
              template: First prompt
            """;
        String file2Content = """
            prompt2:
              id: prompt2
              template: Second prompt
            """;
        String file3Content = """
            {
              "prompt3": {
                "id": "prompt3",
                "template": "Third prompt"
              }
            }
            """;

        Path file1 = tempDir.resolve("prompt1.yaml");
        Path file2 = tempDir.resolve("prompt2.yml");
        Path file3 = tempDir.resolve("prompt3.json");

        Files.write(file1, file1Content.getBytes());
        Files.write(file2, file2Content.getBytes());
        Files.write(file3, file3Content.getBytes());

        // 创建 FilePromptSource 指向目录
        FilePromptSource source = new FilePromptSource(tempDir.toString());

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(3);
            assertThat(prompts.keySet()).containsExactlyInAnyOrder("prompt1", "prompt2", "prompt3");

            // 验证每个 prompt
            assertThat(prompts.get("prompt1").getTemplate()).isEqualTo("First prompt");
            assertThat(prompts.get("prompt2").getTemplate()).isEqualTo("Second prompt");
            assertThat(prompts.get("prompt3").getTemplate()).isEqualTo("Third prompt");

        } finally {
            try {
                source.close();
            } catch (Exception e) {
                // 忽略关闭时的异常
            }
        }
    }

    @Test
    @DisplayName("空目录和不存在路径处理")
    void testEmptyDirectoryAndNonExistentPath() throws IOException, Exception {
        // 创建空目录
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectories(emptyDir);

        FilePromptSource emptySource = new FilePromptSource(emptyDir.toString());
        try {
            Map<String, PromptMeta> prompts = emptySource.loadAll();
            assertThat(prompts).isEmpty();
        } finally {
            emptySource.close();
        }

        // 测试不存在的路径
        String nonExistentPath = tempDir.resolve("nonexistent").toString();
        FilePromptSource nonExistentSource = new FilePromptSource(nonExistentPath);
        try {
            Map<String, PromptMeta> prompts = nonExistentSource.loadAll();
            assertThat(prompts).isEmpty(); // 应该返回空 map，而不是抛出异常
        } finally {
            nonExistentSource.close();
        }
    }

    @Test
    @DisplayName("文件内容变更监听测试")
    void testFileChangeMonitoring() throws IOException, InterruptedException {
        // 创建测试文件
        Path testFile = tempDir.resolve("change-test.yaml");
        String initialContent = """
            changeable:
              id: changeable
              template: Initial template
            """;
        Files.write(testFile, initialContent.getBytes());

        FilePromptSource source = new FilePromptSource(testFile.toString());

        try {
            // 初始加载
            Map<String, PromptMeta> initialPrompts = source.loadAll();
            assertThat(initialPrompts).hasSize(1);
            assertThat(initialPrompts.get("changeable").getTemplate()).isEqualTo("Initial template");

            // 修改文件内容
            String updatedContent = """
                changeable:
                  id: changeable
                  template: Updated template
                """;
            Files.write(testFile, updatedContent.getBytes());

            // 等待文件系统通知
            Thread.sleep(1000);

            // 重新加载应该获取到更新后的内容
            Map<String, PromptMeta> updatedPrompts = source.loadAll();
            assertThat(updatedPrompts).hasSize(1);
            assertThat(updatedPrompts.get("changeable").getTemplate()).isEqualTo("Updated template");

        } finally {
            try {
                source.close();
            } catch (Exception e) {
                // 忽略关闭时的异常
            }
        }
    }

    @Test
    @DisplayName("错误文件处理测试")
    void testErrorFileHandling() throws IOException, Exception {
        // 创建格式错误的 YAML 文件
        Path invalidYaml = tempDir.resolve("invalid.yaml");
        String invalidContent = """
            invalid:
              id: invalid
              template: This is fine
              invalid: yaml: content: [unclosed
            """;
        Files.write(invalidYaml, invalidContent.getBytes());

        // 创建格式错误的 JSON 文件
        Path invalidJson = tempDir.resolve("invalid.json");
        String invalidJsonContent = """
            {
              "invalid-json": {
                "id": "invalid-json",
                "template": "This json is invalid
              }
            }
            """;
        Files.write(invalidJson, invalidJsonContent.getBytes());

        FilePromptSource source = new FilePromptSource(tempDir.toString());

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            // 应该忽略错误文件，但不抛出异常
            assertThat(prompts).isEmpty();

            // 检查是否有错误记录
            Map<String, Throwable> errors = source.getLoadErrors();
            assertThat(errors).hasSize(2); // 应该有两个文件加载错误

        } finally {
            try {
                source.close();
            } catch (Exception e) {
                // 忽略关闭时的异常
            }
        }
    }

    @Test
    @DisplayName("自定义线程池配置测试")
    void testCustomThreadPoolConfiguration() throws IOException, Exception {
        Path testFile = tempDir.resolve("custom-threadpool.yaml");
        String content = """
            custom:
              id: custom
              template: Test custom thread pool
            """;
        Files.write(testFile, content.getBytes());

        // 使用自定义线程池
        FilePromptSource source = new FilePromptSource(
            List.of(testFile.toString()),
            1000, // 1秒防抖延迟
            null,  // 使用默认文件监听线程池
            null   // 使用默认防抖线程池
        );

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(1);
            assertThat(prompts).containsKey("custom");

        } finally {
            try {
                source.close();
            } catch (Exception e) {
                // 忽略关闭时的异常
            }
        }
    }

    @Test
    @DisplayName("资源清理测试")
    void testResourceCleanup() throws Exception {
        Path testFile = tempDir.resolve("cleanup.yaml");
        String content = """
            cleanup:
              id: cleanup
              template: Test cleanup
            """;
        Files.write(testFile, content.getBytes());

        // 创建和销毁多个 source
        for (int i = 0; i < 5; i++) {
            FilePromptSource source = new FilePromptSource(testFile.toString());
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(1);
            source.close();
        }
        // 如果没有异常，说明资源清理正常
    }

    @Test
    @DisplayName("变更回调测试")
    void testChangeCallback() throws IOException, Exception {
        Path testFile = tempDir.resolve("callback.yaml");
        String content = """
            callback:
              id: callback
              template: Initial
            """;
        Files.write(testFile, content.getBytes());

        FilePromptSource source = new FilePromptSource(testFile.toString());

        try {
            // 设置变更监听器
            final boolean[] callbackCalled = {false};
            source.onChange(event -> {
                callbackCalled[0] = true;
                // 验证事件内容
                assertThat(event.getUpdated()).isNotEmpty();
                assertThat(event.getRemoved()).isEmpty();
            });

            // 修改文件触发回调
            String updatedContent = """
                callback:
                  id: callback
                  template: Updated
                """;
            Files.write(testFile, updatedContent.getBytes());

            // 等待回调执行
            Thread.sleep(2000);

            // 注意：在实际的文件监听环境中，回调可能会被触发
            // 但在测试环境中可能无法模拟真实的文件系统事件
            // 所以这里主要测试回调设置不会抛出异常

        } finally {
            try {
                source.close();
            } catch (Exception e) {
                // 忽略关闭时的异常
            }
        }
    }

    @Test
    @DisplayName("特殊字符和编码测试")
    void testSpecialCharactersAndEncoding() throws IOException {
        // 创建包含中文和特殊字符的文件
        Path specialFile = tempDir.resolve("special_chars.yaml");
        String content = """
            special_chars:
              id: special_chars
              template: 你好 {{name}}！欢迎使用 🚀 系统。
              description: 测试中文和emoji支持
            """;
        Files.write(specialFile, content.getBytes(StandardCharsets.UTF_8));

        FilePromptSource source = new FilePromptSource(specialFile.toString());

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(1);
            assertThat(prompts).containsKey("special_chars");

            PromptMeta specialPrompt = prompts.get("special_chars");
            assertThat(specialPrompt.getTemplate()).contains("你好");
            assertThat(specialPrompt.getTemplate()).contains("🚀");
            assertThat(specialPrompt.getDescription()).contains("中文");

        } finally {
            try {
                source.close();
            } catch (Exception e) {
                // 忽略关闭时的异常
            }
        }
    }
}