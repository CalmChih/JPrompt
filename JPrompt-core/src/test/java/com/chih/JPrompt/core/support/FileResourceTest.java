package com.chih.JPrompt.core.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * FileResource 单元测试
 *
 * 测试文件资源包装类的各项功能，包括工厂方法、文件名提取、输入流获取等
 */
@DisplayName("FileResource 测试")
class FileResourceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("从文件系统创建资源 - 正常情况")
    void testFromFile_Success() throws IOException {
        // 创建测试文件
        Path testFile = tempDir.resolve("test.yaml");
        String content = "id: test\nmodel: gpt-3.5-turbo\ntemplate: Hello, {{name}}!";
        Files.write(testFile, content.getBytes());

        // 创建 FileResource
        FileResource resource = FileResource.fromFile(testFile);

        // 验证资源属性
        assertThat(resource.getResourcePath()).isEqualTo(testFile.toString());
        assertThat(resource.getFilename()).isEqualTo("test.yaml");
        assertThat(resource.exists()).isTrue();

        // 验证输入流
        try (InputStream is = resource.getInputStream()) {
            String readContent = new String(is.readAllBytes());
            assertThat(readContent).isEqualTo(content);
        }

        // 验证 ID
        assertThat(resource.getId()).isEqualTo(testFile.toString());
    }

    @Test
    @DisplayName("从文件系统创建资源 - null 参数")
    void testFromFile_NullPath() {
        assertThatThrownBy(() -> FileResource.fromFile(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("File path cannot be null");
    }

    @Test
    @DisplayName("从 Classpath 创建资源 - 正常情况")
    void testFromClasspath_Success() throws IOException, MalformedURLException {
        // 获取类路径下的测试资源
        URL resourceUrl = getClass().getClassLoader().getResource("test-resource.txt");
        if (resourceUrl == null) {
            // 如果测试资源不存在，创建一个临时的
            Path tempResource = tempDir.resolve("test-resource.txt");
            Files.write(tempResource, "test content".getBytes());
            resourceUrl = tempResource.toUri().toURL();
        }

        String resourcePath = "test/path/test-resource.txt";
        FileResource resource = FileResource.fromClasspath(resourceUrl, resourcePath);

        // 验证资源属性
        assertThat(resource.getResourcePath()).isEqualTo(resourcePath);
        assertThat(resource.getFilename()).isEqualTo("test-resource.txt");
        assertThat(resource.exists()).isTrue();

        // 验证输入流
        try (InputStream is = resource.getInputStream()) {
            assertThat(is.read()).isGreaterThan(0);
        }

        // 验证 ID
        assertThat(resource.getId()).isEqualTo(resourcePath);
    }

    @Test
    @DisplayName("从 Classpath 创建资源 - null URL")
    void testFromClasspath_NullUrl() {
        assertThatThrownBy(() -> FileResource.fromClasspath(null, "test.txt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Classpath URL cannot be null");
    }

    @Test
    @DisplayName("从 Classpath 创建资源 - null 资源路径")
    void testFromClasspath_NullResourcePath() throws MalformedURLException {
        final URL testUrl;
        if (getClass().getClassLoader().getResource("test-resource.txt") == null) {
            // 创建一个测试 URL
            testUrl = tempDir.resolve("test.txt").toUri().toURL();
        } else {
            testUrl = getClass().getClassLoader().getResource("test-resource.txt");
        }

        assertThatThrownBy(() -> FileResource.fromClasspath(testUrl, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Resource path cannot be null or empty");
    }

    @Test
    @DisplayName("从 Classpath 创建资源 - 空资源路径")
    void testFromClasspath_EmptyResourcePath() throws MalformedURLException {
        final URL testUrl;
        if (getClass().getClassLoader().getResource("test-resource.txt") == null) {
            // 创建一个测试 URL
            testUrl = tempDir.resolve("test.txt").toUri().toURL();
        } else {
            testUrl = getClass().getClassLoader().getResource("test-resource.txt");
        }

        assertThatThrownBy(() -> FileResource.fromClasspath(testUrl, "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Resource path cannot be null or empty");
    }

    @Test
    @DisplayName("文件名提取 - 文件系统资源")
    void testGetFilename_FileSystemResource() throws IOException {
        // 测试各种文件名情况
        testFilenameExtractionHelper("simple.txt", "simple.txt");
        testFilenameExtractionHelper("file.with.dots.yaml", "file.with.dots.yaml");
        testFilenameExtractionHelper("README.MD", "README.MD");
        testFilenameExtractionHelper("no-extension", "no-extension");

        // 测试包含路径的文件
        Path pathWithSubDir = tempDir.resolve("subdir/nested/file.txt");
        Files.createDirectories(pathWithSubDir.getParent());
        Files.write(pathWithSubDir, "content".getBytes());

        FileResource resource = FileResource.fromFile(pathWithSubDir);
        assertThat(resource.getFilename()).isEqualTo("file.txt");
    }

    @Test
    @DisplayName("文件名提取 - Classpath 资源")
    void testGetFilename_ClasspathResource() throws MalformedURLException, IOException {
        // 创建模拟的 Classpath URL
        Path testFile = tempDir.resolve("classpath-resource.txt");
        Files.write(testFile, "test content".getBytes());
        URL testUrl = testFile.toUri().toURL();

        String resourcePath = "classpath/classpath-resource.txt";
        FileResource resource = FileResource.fromClasspath(testUrl, resourcePath);

        assertThat(resource.getFilename()).isEqualTo("classpath-resource.txt");
    }

    @Test
    @DisplayName("文件名提取 - 特殊情况")
    void testGetFilename_EdgeCases() throws IOException {
        // 测试根路径文件
        Path rootFile = tempDir.resolve("root.txt");
        Files.write(rootFile, "content".getBytes());

        FileResource rootResource = FileResource.fromFile(rootFile);
        assertThat(rootResource.getFilename()).isEqualTo("root.txt");

        // 测试隐藏文件（Unix）
        Path hiddenFile = tempDir.resolve(".hidden");
        Files.write(hiddenFile, "content".getBytes());

        FileResource hiddenResource = FileResource.fromFile(hiddenFile);
        assertThat(hiddenResource.getFilename()).isEqualTo(".hidden");

        // 测试带特殊字符的文件名
        Path specialFile = tempDir.resolve("file with spaces & symbols.txt");
        Files.write(specialFile, "content".getBytes());

        FileResource specialResource = FileResource.fromFile(specialFile);
        assertThat(specialResource.getFilename()).isEqualTo("file with spaces & symbols.txt");
    }

    @Test
    @DisplayName("存在性检查 - 文件系统资源")
    void testExists_FileSystemResource() throws IOException {
        // 测试存在的文件
        Path existingFile = tempDir.resolve("existing.txt");
        Files.write(existingFile, "content".getBytes());
        FileResource existingResource = FileResource.fromFile(existingFile);
        assertThat(existingResource.exists()).isTrue();

        // 测试不存在的文件（但路径存在）
        Path nonExistentFile = tempDir.resolve("non-existent.txt");
        FileResource nonExistentResource = FileResource.fromFile(nonExistentFile);
        assertThat(nonExistentResource.exists()).isFalse();

        // 测试不存在的目录
        Path nonExistentDir = tempDir.resolve("non-existent-dir");
        FileResource nonExistentDirResource = FileResource.fromFile(nonExistentDir);
        assertThat(nonExistentDirResource.exists()).isFalse();
    }

    @Test
    @DisplayName("存在性检查 - Classpath 资源")
    void testExists_ClasspathResource() throws MalformedURLException, IOException {
        // 使用类路径中肯定存在的资源
        URL knownResource = getClass().getResource("/test.properties");
        if (knownResource == null) {
            // 创建一个临时的 Classpath 资源
            Path tempResource = tempDir.resolve("temp-test.properties");
            Files.write(tempResource, "test=value".getBytes());
            URL tempUrl = tempResource.toUri().toURL();
            FileResource resource = FileResource.fromClasspath(tempUrl, "temp-test.properties");
            assertThat(resource.exists()).isTrue();
        } else {
            FileResource resource = FileResource.fromClasspath(knownResource, "test.properties");
            assertThat(resource.exists()).isTrue();
        }
    }

    @Test
    @DisplayName("输入流获取 - 文件系统资源")
    void testGetInputStream_FileSystemResource() throws IOException {
        Path testFile = tempDir.resolve("stream-test.txt");
        String content = "This is test content for stream reading.";
        Files.write(testFile, content.getBytes());

        FileResource resource = FileResource.fromFile(testFile);

        try (InputStream is = resource.getInputStream()) {
            String readContent = new String(is.readAllBytes());
            assertThat(readContent).isEqualTo(content);
        }

        // 验证可以重复获取输入流（每次都是新的流）
        try (InputStream is2 = resource.getInputStream()) {
            String readContent2 = new String(is2.readAllBytes());
            assertThat(readContent2).isEqualTo(content);
        }
    }

    @Test
    @DisplayName("输入流获取 - Classpath 资源")
    void testGetInputStream_ClasspathResource() throws MalformedURLException, IOException {
        URL testUrl = getClass().getResource("/test.properties");
        if (testUrl == null) {
            // 创建临时资源用于测试
            Path tempResource = tempDir.resolve("test.properties");
            Files.write(tempResource, "test=value".getBytes());
            testUrl = tempResource.toUri().toURL();

            FileResource resource = FileResource.fromClasspath(testUrl, "test.properties");

            try (InputStream is = resource.getInputStream()) {
                assertThat(is.read()).isGreaterThan(0);
            }
        } else {
            FileResource resource = FileResource.fromClasspath(testUrl, "test.properties");

            try (InputStream is = resource.getInputStream()) {
                assertThat(is.read()).isGreaterThan(0);
            }
        }
    }

    @Test
    @DisplayName("资源路径一致性")
    void testResourcePathConsistency() throws IOException {
        // 文件系统资源
        Path file = tempDir.resolve("consistency-test.yaml");
        Files.write(file, "content".getBytes());

        FileResource fileResource = FileResource.fromFile(file);
        assertThat(fileResource.getResourcePath()).isEqualTo(file.toString());

        // Classpath 资源
        URL url = getClass().getResource("/test.properties");
        if (url != null) {
            FileResource classpathResource = FileResource.fromClasspath(url, "test.properties");
            assertThat(classpathResource.getResourcePath()).isEqualTo("test.properties");
        }
    }

    @Test
    @DisplayName("大文件处理")
    void testLargeFileHandling() throws IOException {
        // 创建一个较大的测试文件（1MB）
        Path largeFile = tempDir.resolve("large-test.txt");
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < 1024 * 100; i++) {
            contentBuilder.append("This is line ").append(i).append(" of the large test file.\n");
        }
        String largeContent = contentBuilder.toString();
        Files.write(largeFile, largeContent.getBytes());

        FileResource resource = FileResource.fromFile(largeFile);

        // 验证可以读取大文件
        try (InputStream is = resource.getInputStream()) {
            String readContent = new String(is.readAllBytes());
            assertThat(readContent).hasSize(largeContent.length());
            assertThat(readContent).startsWith("This is line 0 of the large test file.");
            assertThat(readContent).endsWith("This is line 102399 of the large test file.\n");
        }
    }

    @Test
    @DisplayName("资源比较和哈希码")
    void testResourceEqualityAndHashCode() throws IOException {
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.write(file1, "content1".getBytes());
        Files.write(file2, "content2".getBytes());

        FileResource resource1a = FileResource.fromFile(file1);
        FileResource resource1b = FileResource.fromFile(file1);
        FileResource resource2 = FileResource.fromFile(file2);

        // 相同文件应该相等
        assertThat(resource1a).isEqualTo(resource1b);
        assertThat(resource1a.hashCode()).isEqualTo(resource1b.hashCode());

        // 不同文件应该不相等
        assertThat(resource1a).isNotEqualTo(resource2);
        assertThat(resource1a.hashCode()).isNotEqualTo(resource2.hashCode());

        // null 检查
        assertThat(resource1a).isNotNull();
        assertThat(resource1a).isNotEqualTo(null);
    }

    @Test
    @DisplayName("资源转换为字符串表示")
    void testResourceToString() throws IOException {
        Path file = tempDir.resolve("string-test.yaml");
        Files.write(file, "id: test".getBytes());

        FileResource resource = FileResource.fromFile(file);
        String stringRepresentation = resource.toString();

        // 字符串表示应该包含关键信息
        assertThat(stringRepresentation).contains("FileResource");
        assertThat(stringRepresentation).contains(file.toString());
    }

    @Test
    @DisplayName("处理特殊路径字符")
    void testSpecialPathCharacters() throws IOException {
        // 测试包含空格的路径
        Path spacePath = tempDir.resolve("path with spaces/file.txt");
        Files.createDirectories(spacePath.getParent());
        Files.write(spacePath, "content".getBytes());

        FileResource spaceResource = FileResource.fromFile(spacePath);
        assertThat(spaceResource.getFilename()).isEqualTo("file.txt");
        assertThat(spaceResource.getResourcePath()).contains("path with spaces");

        // 测试包含Unicode字符的路径
        Path unicodePath = tempDir.resolve("测试文件.txt");
        Files.write(unicodePath, "content".getBytes());

        FileResource unicodeResource = FileResource.fromFile(unicodePath);
        assertThat(unicodeResource.getFilename()).isEqualTo("测试文件.txt");
        assertThat(unicodeResource.getResourcePath()).contains("测试文件.txt");
    }

    /**
     * 辅助方法：测试文件名提取
     */
    private void testFilenameExtractionHelper(String filename, String expectedFilename) throws IOException {
        Path testFile = tempDir.resolve(filename);
        Files.write(testFile, "test content".getBytes());

        FileResource resource = FileResource.fromFile(testFile);
        assertThat(resource.getFilename()).isEqualTo(expectedFilename);
    }
}