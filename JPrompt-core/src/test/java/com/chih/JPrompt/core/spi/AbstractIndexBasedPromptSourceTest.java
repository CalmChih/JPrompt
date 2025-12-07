package com.chih.JPrompt.core.spi;

import com.chih.JPrompt.core.support.FileResource;
import com.chih.JPrompt.core.domain.PromptMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * AbstractIndexBasedPromptSource 单元测试
 *
 * 测试抽象基类的索引管理、缓存机制、热更新等核心功能
 * 使用具体的测试实现类来验证抽象类的行为
 */
@DisplayName("AbstractIndexBasedPromptSource 测试")
class AbstractIndexBasedPromptSourceTest {

    @TempDir
    Path tempDir;

    /**
     * 创建测试用的具体实现类
     */
    private static class TestIndexBasedPromptSource extends AbstractIndexBasedPromptSource<FileResource> {

        private final Map<FileResource, String> testResources = new HashMap<>();

        public TestIndexBasedPromptSource() {
            super();
        }

        // 添加测试资源
        public void addTestResource(FileResource resource, String content) {
            testResources.put(resource, content);
        }

        @Override
        protected InputStream openStream(FileResource resource) throws Exception {
            String content = testResources.get(resource);
            if (content == null) {
                throw new IOException("Resource not found: " + resource.getId());
            }
            return new java.io.ByteArrayInputStream(content.getBytes());
        }

        @Override
        protected String getResourceId(FileResource resource) {
            return resource.getId();
        }

        @Override
        protected boolean exists(FileResource resource) {
            return testResources.containsKey(resource);
        }

        @Override
        protected FileResource resolveResourceFromFile(File file) {
            return FileResource.fromFile(file.toPath());
        }

        @Override
        protected String getResourceDescription(FileResource resource) {
            return resource.getResourcePath();
        }

        @Override
        protected String getResourceFilename(FileResource resource) {
            return resource.getFilename();
        }
    }

    @Test
    @DisplayName("基本索引管理功能")
    void testBasicIndexManagement() throws IOException {
        TestIndexBasedPromptSource source = new TestIndexBasedPromptSource();

        // 创建测试资源 - 使用更简单的 YAML 格式
        Path testFile1 = tempDir.resolve("test1.yaml");
        Path testFile2 = tempDir.resolve("test2.yaml");

        String content1 = "greeting:\n  id: greeting\n  template: Hello {{name}}!";
        String content2 = "welcome:\n  id: welcome\n  template: Welcome {{name}}!";

        Files.write(testFile1, content1.getBytes());
        Files.write(testFile2, content2.getBytes());

        FileResource resource1 = FileResource.fromFile(testFile1);
        FileResource resource2 = FileResource.fromFile(testFile2);

        source.addTestResource(resource1, content1);
        source.addTestResource(resource2, content2);

        // 测试索引管理
        source.safeLoadResource(resource1);
        source.safeLoadResource(resource2);

        // 验证索引状态
        Map<String, PromptMeta> allPrompts = source.loadAll();
        assertThat(allPrompts.keySet()).contains("greeting", "welcome");
        assertThat(allPrompts.containsKey("greeting")).isTrue();
        assertThat(allPrompts.containsKey("welcome")).isTrue();
        assertThat(allPrompts.containsKey("nonexistent")).isFalse();

        // 验证资源获取
        PromptMeta greetingMeta = source.load("greeting");
        PromptMeta welcomeMeta = source.load("welcome");

        assertThat(greetingMeta).isNotNull();
        assertThat(greetingMeta.getId()).isEqualTo("greeting");
        assertThat(greetingMeta.getTemplate()).isEqualTo("Hello {{name}}!");

        assertThat(welcomeMeta).isNotNull();
        assertThat(welcomeMeta.getId()).isEqualTo("welcome");
        assertThat(welcomeMeta.getTemplate()).isEqualTo("Welcome {{name}}!");
    }

    @Test
    @DisplayName("双向索引一致性")
    void testBidirectionalIndexConsistency() throws IOException {
        TestIndexBasedPromptSource source = new TestIndexBasedPromptSource();

        // 创建测试资源
        Path testFile = tempDir.resolve("multi-prompts.yaml");
        String content = """
            prompt1:
              id: prompt1
              template: Template 1
            prompt2:
              id: prompt2
              template: Template 2
            """;

        Files.write(testFile, content.getBytes());
        FileResource resource = FileResource.fromFile(testFile);
        source.addTestResource(resource, content);

        source.safeLoadResource(resource);

        // 验证双向索引一致性
        Map<String, PromptMeta> allPrompts = source.loadAll();
        assertThat(allPrompts).hasSize(2);
        assertThat(allPrompts.keySet()).contains("prompt1", "prompt2");

        // 验证每个 key 都能正确获取到 PromptMeta
        for (String key : allPrompts.keySet()) {
            PromptMeta meta = source.load(key);
            assertThat(meta).isNotNull();
            assertThat(meta.getId()).isEqualTo(key);
        }
    }

    @Test
    @DisplayName("缓存机制验证")
    void testCachingMechanism() throws IOException {
        TestIndexBasedPromptSource source = new TestIndexBasedPromptSource();

        Path testFile = tempDir.resolve("cache-test.yaml");
        String content = """
            cached:
              id: cached
              template: Cached template
            """;
        Files.write(testFile, content.getBytes());

        FileResource resource = FileResource.fromFile(testFile);
        source.addTestResource(resource, content);

        // 第一次加载
        source.safeLoadResource(resource);
        PromptMeta firstResult = source.load("cached");
        assertThat(firstResult).isNotNull();

        // 第二次获取应该从缓存中返回
        PromptMeta secondResult = source.load("cached");
        assertThat(secondResult).isNotNull();
        // 验证内容相同
        assertThat(secondResult.getId()).isEqualTo(firstResult.getId());
        assertThat(secondResult.getTemplate()).isEqualTo(firstResult.getTemplate());

        // 验证索引大小
        assertThat(source.keyToIndex.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("资源更新测试")
    void testResourceUpdate() throws IOException {
        TestIndexBasedPromptSource source = new TestIndexBasedPromptSource();

        Path testFile = tempDir.resolve("update-test.yaml");
        String originalContent = """
            updatable:
              id: updatable
              template: Original template
            """;
        String updatedContent = """
            updatable:
              id: updatable
              template: Updated template
            """;

        Files.write(testFile, originalContent.getBytes());
        FileResource resource = FileResource.fromFile(testFile);

        // 加载原始内容
        source.addTestResource(resource, originalContent);
        source.safeLoadResource(resource);

        PromptMeta originalMeta = source.load("updatable");
        assertThat(originalMeta.getTemplate()).isEqualTo("Original template");

        // 更新内容
        source.addTestResource(resource, updatedContent);
        source.safeLoadResource(resource);

        PromptMeta updatedMeta = source.load("updatable");
        assertThat(updatedMeta.getTemplate()).isEqualTo("Updated template");
    }

    @Test
    @DisplayName("批量操作性能测试")
    void testBatchOperationsPerformance() throws IOException {
        TestIndexBasedPromptSource source = new TestIndexBasedPromptSource();

        int resourceCount = 100;
        List<FileResource> resources = new ArrayList<>();

        // 创建大量测试资源
        for (int i = 0; i < resourceCount; i++) {
            Path testFile = tempDir.resolve("batch-" + i + ".yaml");
            String content = String.format("""
                    batch%d:
                      id: batch%d
                      template: Batch template %d
                    """, i, i, i);
            Files.write(testFile, content.getBytes());

            FileResource resource = FileResource.fromFile(testFile);
            source.addTestResource(resource, content);
            resources.add(resource);
            source.safeLoadResource(resource);
        }

        // 验证批量加载结果
        Map<String, PromptMeta> allPrompts = source.loadAll();
        assertThat(allPrompts).hasSize(resourceCount);
        assertThat(source.keyToIndex.size()).isEqualTo(resourceCount);

        // 测试批量获取性能
        long startTime = System.currentTimeMillis();
        for (String key : allPrompts.keySet()) {
            PromptMeta meta = source.load(key);
            assertThat(meta).isNotNull();
        }
        long endTime = System.currentTimeMillis();

        // 批量获取应该很快（小于100ms）
        assertThat(endTime - startTime).isLessThan(200);
    }

    @Test
    @DisplayName("错误处理和恢复")
    void testErrorHandlingAndRecovery() throws IOException {
        TestIndexBasedPromptSource source = new TestIndexBasedPromptSource();

        // 测试加载不存在资源
        Path nonExistentFile = tempDir.resolve("nonexistent.yaml");
        FileResource nonExistentResource = FileResource.fromFile(nonExistentFile);

        // 应该不抛出异常，但不会添加到索引
        source.safeLoadResource(nonExistentResource);
        assertThat(source.keyToIndex.size()).isEqualTo(0);

        // 测试加载损坏的资源
        Path corruptedFile = tempDir.resolve("corrupted.yaml");
        String corruptedContent = "invalid: yaml: content: [";
        Files.write(corruptedFile, corruptedContent.getBytes());

        FileResource corruptedResource = FileResource.fromFile(corruptedFile);
        source.addTestResource(corruptedResource, corruptedContent);

        // 应该不抛出异常，但不会添加到索引
        source.safeLoadResource(corruptedResource);
        assertThat(source.keyToIndex.size()).isEqualTo(0);

        // 测试恢复：加载正常资源
        Path normalFile = tempDir.resolve("normal.yaml");
        String normalContent = """
            normal:
              id: normal
              template: Normal template
            """;
        Files.write(normalFile, normalContent.getBytes());

        FileResource normalResource = FileResource.fromFile(normalFile);
        source.addTestResource(normalResource, normalContent);
        source.safeLoadResource(normalResource);

        // 系统应该恢复正常
        assertThat(source.keyToIndex.size()).isEqualTo(1);
        Map<String, PromptMeta> allPrompts = source.loadAll();
        assertThat(allPrompts.containsKey("normal")).isTrue();
    }

    @Test
    @DisplayName("资源描述和文件名提取")
    void testResourceDescriptionAndFilenameExtraction() throws IOException {
        TestIndexBasedPromptSource source = new TestIndexBasedPromptSource();

        // 测试各种路径格式的资源
        Path simpleFile = tempDir.resolve("simple.yaml");
        Path nestedFile = tempDir.resolve("nested/deep/test.yaml");

        Files.createDirectories(nestedFile.getParent());
        String simpleContent = """
            simple:
              id: simple
              template: Simple
            """;
        String nestedContent = """
            nested:
              id: nested
              template: Nested
            """;

        Files.write(simpleFile, simpleContent.getBytes());
        Files.write(nestedFile, nestedContent.getBytes());

        FileResource simpleResource = FileResource.fromFile(simpleFile);
        FileResource nestedResource = FileResource.fromFile(nestedFile);

        source.addTestResource(simpleResource, simpleContent);
        source.addTestResource(nestedResource, nestedContent);

        // 验证描述获取
        String simpleDescription = source.getResourceDescription(simpleResource);
        String nestedDescription = source.getResourceDescription(nestedResource);

        assertThat(simpleDescription).contains("simple.yaml");
        assertThat(nestedDescription).contains("test.yaml");

        // 验证文件名提取
        String simpleFilename = source.getResourceFilename(simpleResource);
        String nestedFilename = source.getResourceFilename(nestedResource);

        assertThat(simpleFilename).isEqualTo("simple.yaml");
        assertThat(nestedFilename).isEqualTo("test.yaml");
    }

    @Test
    @DisplayName("索引状态监控")
    void testIndexStatusMonitoring() throws IOException {
        TestIndexBasedPromptSource source = new TestIndexBasedPromptSource();

        // 初始状态
        Map<String, PromptMeta> initialPrompts = source.loadAll();
        assertThat(initialPrompts).isEmpty();
        assertThat(source.keyToIndex.size()).isEqualTo(0);

        // 添加资源
        Path testFile = tempDir.resolve("status.yaml");
        String content = """
            status:
              id: status
              template: Status test
            """;
        Files.write(testFile, content.getBytes());

        FileResource resource = FileResource.fromFile(testFile);
        source.addTestResource(resource, content);
        source.safeLoadResource(resource);

        // 验证状态变化
        Map<String, PromptMeta> updatedPrompts = source.loadAll();
        assertThat(updatedPrompts).hasSize(1);
        assertThat(source.keyToIndex.size()).isEqualTo(1);
        assertThat(updatedPrompts.containsKey("status")).isTrue();

        // 获取详细状态信息
        PromptMeta meta = source.load("status");
        assertThat(meta).isNotNull();
        assertThat(meta.getId()).isEqualTo("status");
        assertThat(meta.getTemplate()).isEqualTo("Status test");
    }

    @Test
    @DisplayName("LoadErrors 获取测试")
    void testGetLoadErrors() throws IOException {
        TestIndexBasedPromptSource source = new TestIndexBasedPromptSource();

        // 初始状态应该没有错误
        Map<String, Throwable> errors = source.getLoadErrors();
        assertThat(errors).isEmpty();

        // 测试加载损坏资源会产生错误
        Path corruptedFile = tempDir.resolve("corrupted.yaml");
        String corruptedContent = "invalid: yaml: content: [";
        Files.write(corruptedFile, corruptedContent.getBytes());

        FileResource corruptedResource = FileResource.fromFile(corruptedFile);
        source.addTestResource(corruptedResource, corruptedContent);
        source.safeLoadResource(corruptedResource);

        // 应该记录了错误信息
        errors = source.getLoadErrors();
        assertThat(errors).isNotEmpty();
    }
}