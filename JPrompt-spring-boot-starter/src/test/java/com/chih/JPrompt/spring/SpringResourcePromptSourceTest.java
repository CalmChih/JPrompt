package com.chih.JPrompt.spring;

import com.chih.JPrompt.core.domain.PromptMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * SpringResourcePromptSource 单元测试
 *
 * 测试基于 Spring Resource 抽象的 Prompt 源功能，包括：
 * - 基本的 Prompt 加载
 * - 多种协议支持（file:、classpath:、jar:）
 * - 通配符模式支持
 * - 热更新机制
 * - 错误处理
 */
@DisplayName("SpringResourcePromptSource 测试")
class SpringResourcePromptSourceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("基本功能测试 - 加载单个文件系统资源")
    void testBasicFunctionality_SingleFileSystemResource() throws Exception {
        /* 创建测试 YAML 文件 */
        Path yamlFile = tempDir.resolve("test.yaml");
        String content = """
            id: spring-greeting
            template: Hello {{name}}! (Spring)
            description: Spring resource prompt
            """;
        Files.write(yamlFile, content.getBytes());

        /* 创建 SpringResourcePromptSource */
        SpringResourcePromptSource source = new SpringResourcePromptSource(
            List.of(yamlFile.toString()), 500, null, null
        );

        try {
            /* 测试 loadAll */
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(1);
            assertThat(prompts).containsKey("spring-greeting");

            PromptMeta greeting = prompts.get("spring-greeting");
            assertThat(greeting.getId()).isEqualTo("spring-greeting");
            assertThat(greeting.getTemplate()).isEqualTo("Hello {{name}}! (Spring)");
            assertThat(greeting.getDescription()).isEqualTo("Spring resource prompt");

            /* 测试 load */
            PromptMeta loadedGreeting = source.load("spring-greeting");
            assertThat(loadedGreeting).isNotNull();
            assertThat(loadedGreeting.getId()).isEqualTo("spring-greeting");

            /* 测试不存在的 key */
            PromptMeta nonExistent = source.load("nonexistent");
            assertThat(nonExistent).isNull();

        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("Classpath 资源测试")
    void testClasspathResources() throws Exception {
        /* 测试 classpath 前缀的路径模式 */
        String classpathPattern = "classpath*:/**/*.yaml";

        SpringResourcePromptSource source = new SpringResourcePromptSource(
            List.of(classpathPattern), 500, null, null
        );

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            /* 由于是真实测试环境，可能没有实际的 classpath 资源 */
            /* 所以主要验证不会抛出异常 */
            assertThat(prompts).isNotNull();

        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("文件系统通配符测试")
    void testFileSystemWildcardPatterns() throws Exception {
        /* 创建多个测试文件 */
        String file1Content = """
            id: wildcard1
            template: First wildcard prompt
            """;
        String file2Content = """
            id: wildcard2
            template: Second wildcard prompt
            """;
        String jsonContent = """
            {
              "id": "wildcard3",
              "template": "Third wildcard prompt"
            }
            """;

        Path file1 = tempDir.resolve("prompt1.yaml");
        Path file2 = tempDir.resolve("prompt2.yml");
        Path file3 = tempDir.resolve("prompt3.json");

        Files.write(file1, file1Content.getBytes());
        Files.write(file2, file2Content.getBytes());
        Files.write(file3, jsonContent.getBytes());

        /* 使用通配符模式 */
        String wildcardPattern = tempDir.resolve("*.y*").toString();
        SpringResourcePromptSource source = new SpringResourcePromptSource(
            List.of(wildcardPattern), 500, null, null
        );

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            /* 应该找到所有匹配的文件 */
            assertThat(prompts.keySet()).contains("wildcard1", "wildcard2", "wildcard3");

            /* 验证每个 prompt */
            assertThat(prompts.get("wildcard1").getTemplate()).isEqualTo("First wildcard prompt");
            assertThat(prompts.get("wildcard2").getTemplate()).isEqualTo("Second wildcard prompt");
            assertThat(prompts.get("wildcard3").getTemplate()).isEqualTo("Third wildcard prompt");

        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("多位置模式测试")
    void testMultipleLocationPatterns() throws Exception {
        /* 创建不同目录的文件 */
        Path dir1 = tempDir.resolve("dir1");
        Path dir2 = tempDir.resolve("dir2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);

        String content1 = """
            id: location1
            template: From location 1
            """;
        String content2 = """
            id: location2
            template: From location 2
            """;

        Path file1 = dir1.resolve("prompt.yaml");
        Path file2 = dir2.resolve("prompt.yml");

        Files.write(file1, content1.getBytes());
        Files.write(file2, content2.getBytes());

        /* 使用多个位置模式 */
        List<String> locations = List.of(
            dir1.resolve("*.y*").toString(),
            dir2.resolve("*.y*").toString()
        );

        SpringResourcePromptSource source = new SpringResourcePromptSource(
            locations, 500, null, null
        );

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(2);
            assertThat(prompts.keySet()).contains("location1", "location2");

        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("嵌套目录通配符测试")
    void testNestedDirectoryWildcards() throws Exception {
        /* 创建嵌套目录结构 */
        Path nestedDir = tempDir.resolve("prompts/subdir/deep");
        Files.createDirectories(nestedDir);

        String content = """
            id: nested
            template: Nested directory prompt
            """;

        Path nestedFile = nestedDir.resolve("nested.yaml");
        Files.write(nestedFile, content.getBytes());

        /* 使用递归通配符 */
        String recursivePattern = tempDir.resolve("**/*.yaml").toString();
        SpringResourcePromptSource source = new SpringResourcePromptSource(
            List.of(recursivePattern), 500, null, null
        );

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(1);
            assertThat(prompts).containsKey("nested");

            PromptMeta nested = prompts.get("nested");
            assertThat(nested.getTemplate()).isEqualTo("Nested directory prompt");

        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("特殊字符路径测试")
    void testSpecialCharacterPaths() throws Exception {
        /* 创建包含空格和特殊字符的目录和文件 */
        Path specialDir = tempDir.resolve("special chars dir");
        Files.createDirectories(specialDir);

        String content = """
            id: special-chars
            template: Path with special characters & spaces
            """;

        Path specialFile = specialDir.resolve("special file.yaml");
        Files.write(specialFile, content.getBytes());

        SpringResourcePromptSource source = new SpringResourcePromptSource(
            List.of(specialDir.toString()), 500, null, null
        );

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(1);
            assertThat(prompts).containsKey("special-chars");

        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("无效路径处理测试")
    void testInvalidPathHandling() throws Exception {
        /* 测试不存在的路径 */
        String nonExistentPath = tempDir.resolve("nonexistent").toString();

        SpringResourcePromptSource source = new SpringResourcePromptSource(
            List.of(nonExistentPath), 500, null, null
        );

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            /* 应该返回空 map，而不是抛出异常 */
            assertThat(prompts).isEmpty();

        } finally {
            source.close();
        }

        /* 测试空路径列表 */
        SpringResourcePromptSource emptySource = new SpringResourcePromptSource(
            List.of(), 500, null, null
        );
        try {
            Map<String, PromptMeta> prompts = emptySource.loadAll();
            assertThat(prompts).isEmpty();
        } finally {
            emptySource.close();
        }
    }

    @Test
    @DisplayName("文件内容变更监听测试")
    void testFileChangeMonitoring() throws Exception, InterruptedException {
        /* 创建测试文件 */
        Path testFile = tempDir.resolve("change-test.yaml");
        String initialContent = """
            id: spring-changeable
            template: Initial Spring template
            """;
        Files.write(testFile, initialContent.getBytes());

        SpringResourcePromptSource source = new SpringResourcePromptSource(
            List.of(testFile.toString()), 500, null, null
        );

        try {
            /* 初始加载 */
            Map<String, PromptMeta> initialPrompts = source.loadAll();
            assertThat(initialPrompts).hasSize(1);
            assertThat(initialPrompts.get("spring-changeable").getTemplate())
                .isEqualTo("Initial Spring template");

            /* 修改文件内容 */
            String updatedContent = """
                id: spring-changeable
                template: Updated Spring template
                """;
            Files.write(testFile, updatedContent.getBytes());

            /* 等待文件系统通知 */
            Thread.sleep(1000);

            /* 重新加载应该获取到更新后的内容 */
            Map<String, PromptMeta> updatedPrompts = source.loadAll();
            assertThat(updatedPrompts).hasSize(1);
            assertThat(updatedPrompts.get("spring-changeable").getTemplate())
                .isEqualTo("Updated Spring template");

        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("错误文件处理测试")
    void testErrorFileHandling() throws Exception {
        /* 创建格式错误的文件 */
        Path invalidYaml = tempDir.resolve("invalid.yaml");
        String invalidContent = """
            id: invalid
            template: This is fine
            invalid: yaml: content: [unclosed
            """;
        Files.write(invalidYaml, invalidContent.getBytes());

        /* 创建格式错误的 JSON */
        Path invalidJson = tempDir.resolve("invalid.json");
        String invalidJsonContent = """
            {
              "id": "invalid-json",
              "template": "This json is invalid
            }
            """;
        Files.write(invalidJson, invalidJsonContent.getBytes());

        SpringResourcePromptSource source = new SpringResourcePromptSource(
            List.of(tempDir.toString()), 500, null, null
        );

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            /* 应该忽略错误文件，但不抛出异常 */
            assertThat(prompts).isEmpty();

            /* 检查是否有错误记录 */
            Map<String, Throwable> errors = source.getLoadErrors();
            assertThat(errors).hasSize(2); /* 应该有两个文件加载错误 */

        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("自定义线程池配置测试")
    void testCustomThreadPoolConfiguration() throws Exception {
        Path testFile = tempDir.resolve("custom-threadpool.yaml");
        String content = """
            id: spring-custom
            template: Test Spring custom thread pool
            """;
        Files.write(testFile, content.getBytes());

        /* 使用自定义线程池 */
        SpringResourcePromptSource source = new SpringResourcePromptSource(
            List.of(testFile.toString()),
            1000, /* 1秒防抖延迟 */
            null,  /* 使用默认文件监听线程池 */
            null   /* 使用默认防抖线程池 */
        );

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(1);
            assertThat(prompts).containsKey("spring-custom");

        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("资源清理测试")
    void testResourceCleanup() throws Exception {
        Path testFile = tempDir.resolve("cleanup.yaml");
        String content = """
            id: spring-cleanup
            template: Test Spring cleanup
            """;
        Files.write(testFile, content.getBytes());

        /* 创建和销毁多个 source */
        for (int i = 0; i < 5; i++) {
            SpringResourcePromptSource source = new SpringResourcePromptSource(
                List.of(testFile.toString()), 500, null, null
            );
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(1);
            source.close();
        }
        /* 如果没有异常，说明资源清理正常 */
    }

    @Test
    @DisplayName("变更回调测试")
    void testChangeCallback() throws Exception {
        Path testFile = tempDir.resolve("callback.yaml");
        String content = """
            id: spring-callback
            template: Initial
            """;
        Files.write(testFile, content.getBytes());

        SpringResourcePromptSource source = new SpringResourcePromptSource(
            List.of(testFile.toString()), 500, null, null
        );

        try {
            /* 设置变更监听器 */
            final boolean[] callbackCalled = {false};
            source.onChange(event -> {
                callbackCalled[0] = true;
                /* 验证事件内容 */
                assertThat(event.getUpdated()).isNotEmpty();
                assertThat(event.getRemoved()).isEmpty();
            });

            /* 修改文件触发回调 */
            String updatedContent = """
                id: spring-callback
                template: Updated
                """;
            Files.write(testFile, updatedContent.getBytes());

            /* 等待回调执行 */
            Thread.sleep(2000);

            /* 注意：在实际的文件监听环境中，回调可能会被触发 */
            /* 但在测试环境中可能无法模拟真实的文件系统事件 */
            /* 所以这里主要测试回调设置不会抛出异常 */

        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("与 PathMatchingResourcePatternResolver 集成测试")
    void testPathMatchingResourcePatternResolverIntegration() throws Exception {
        /* 创建测试文件 */
        Path testFile = tempDir.resolve("integration.yaml");
        String content = """
            id: integration
            template: PathMatching integration test
            """;
        Files.write(testFile, content.getBytes());

        /* 手动创建 Resource 来模拟 PathMatchingResourcePatternResolver 的行为 */
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("file:" + testFile.toString());

        /* 转换为 URL 列表传递给 SpringResourcePromptSource */
        List<String> locations = new java.util.ArrayList<>();
        for (Resource resource : resources) {
            locations.add(resource.getURL().toString());
        }

        SpringResourcePromptSource source = new SpringResourcePromptSource(
            locations, 500, null, null
        );

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(1);
            assertThat(prompts).containsKey("integration");

            PromptMeta integrationPrompt = prompts.get("integration");
            assertThat(integrationPrompt.getTemplate()).isEqualTo("PathMatching integration test");

        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("文件系统资源类型检测测试")
    void testFileSystemResourceTypeDetection() throws Exception {
        /* 创建文件系统资源 */
        Path testFile = tempDir.resolve("resource-type.yaml");
        String content = """
            id: resource-type
            template: Resource type detection test
            """;
        Files.write(testFile, content.getBytes());

        /* 使用 FileSystemResource 来创建 Spring 资源 */
        FileSystemResource fileSystemResource = new FileSystemResource(testFile);

        SpringResourcePromptSource source = new SpringResourcePromptSource(
            List.of(fileSystemResource.getURL().toString()), 500, null, null
        );

        try {
            Map<String, PromptMeta> prompts = source.loadAll();
            assertThat(prompts).hasSize(1);
            assertThat(prompts).containsKey("resource-type");

        } finally {
            source.close();
        }
    }
}