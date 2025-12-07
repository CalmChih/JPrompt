package com.chih.JPrompt.core.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 文件资源包装类
 * <p>
 * 统一文件系统和 Classpath 资源的访问接口，为 AbstractIndexBasedPromptSource
 * 提供泛型支持。该类使用适配器模式，屏蔽了不同资源类型的差异。
 * </p>
 *
 * <h3>支持资源类型：</h3>
 * <ul>
 *   <li>文件系统资源：本地文件系统中的文件</li>
 *   <li>Classpath 资源：打包在 JAR 文件中或类路径中的资源</li>
 * </ul>
 *
 * <h3>设计特点：</h3>
 * <ul>
 *   <li>单一职责：只负责资源的统一表示和访问</li>
 *   <li>不可变性：所有字段为 final，线程安全</li>
 *   <li>适配器模式：统一不同类型资源的访问接口</li>
 *   <li>工厂方法：提供便捷的创建方法</li>
 * </ul>
 *
 * @author lizhiyuan
 * @since 2025/12/07
 */
public class FileResource {

    /**
     * 文件系统路径
     * <p>
     * 当资源来自文件系统时，使用该字段存储 NIO Path 对象。
     * Path 对象提供了跨平台的文件系统操作能力。
     * </p>
     */
    private final Path filePath;

    /**
     * Classpath 资源 URL
     * <p>
     * 当资源来自 Classpath 时，使用该字段存储 URL 对象。
     * 支持多种 URL 协议：file:, jar:, http: 等。
     * </p>
     */
    private final URL classpathUrl;

    /**
     * 资源路径标识
     * <p>
     * 用于日志记录和调试的资源标识符，不用于实际的资源访问。
     * 对于文件资源，通常是绝对路径；对于 Classpath 资源，通常是相对路径。
     * </p>
     */
    private final String resourcePath;

    /**
     * 私有构造函数，强制使用工厂方法创建实例
     *
     * @param filePath 文件系统路径，可为 null（Classpath 资源）
     * @param classpathUrl Classpath URL，可为 null（文件系统资源）
     * @param resourcePath 资源路径标识，不能为 null
     */
    private FileResource(Path filePath, URL classpathUrl, String resourcePath) {
        this.filePath = filePath;
        this.classpathUrl = classpathUrl;
        this.resourcePath = resourcePath;
    }

    /**
     * 创建文件系统资源
     *
     * 从本地文件系统中的文件创建 FileResource 实例。
     * 通常用于处理用户配置的文件路径、开发环境中的资源文件等场景。
     *
     * <h3>使用示例：</h3>
     * <pre>{@code
     * Path promptFile = Paths.get("/path/to/prompts/greeting.yaml");
     * FileResource resource = FileResource.fromFile(promptFile);
     * InputStream is = resource.getInputStream();
     * }</pre>
     *
     * @param filePath 文件系统中的文件路径，不能为 null
     * @return FileResource 实例
     * @throws IllegalArgumentException 如果 filePath 为 null
     */
    public static FileResource fromFile(Path filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }
        return new FileResource(filePath, null, filePath.toString());
    }

    /**
     * 创建 Classpath 资源
     *
     * 从 Classpath 中的资源创建 FileResource 实例。
     * 通常用于处理打包在 JAR 文件中的资源文件、src/main/resources 目录下的文件等。
     *
     * <h3>使用示例：</h3>
     * <pre>{@code
     * URL resourceUrl = getClass().getClassLoader().getResource("prompts/default.yaml");
     * FileResource resource = FileResource.fromClasspath(resourceUrl, "prompts/default.yaml");
     * InputStream is = resource.getInputStream();
     * }</pre>
     *
     * @param classpathUrl Classpath 资源的 URL，不能为 null
     * @param resourcePath 资源的相对路径，用于日志记录，不能为 null
     * @return FileResource 实例
     * @throws IllegalArgumentException 如果参数为 null
     */
    public static FileResource fromClasspath(URL classpathUrl, String resourcePath) {
        if (classpathUrl == null) {
            throw new IllegalArgumentException("Classpath URL cannot be null");
        }
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource path cannot be null or empty");
        }
        return new FileResource(null, classpathUrl, resourcePath);
    }

    /**
     * 打开输入流
     */
    public InputStream getInputStream() throws IOException {
        if (filePath != null) {
            return Files.newInputStream(filePath);
        } else if (classpathUrl != null) {
            return classpathUrl.openStream();
        } else {
            throw new IOException("Resource is not backed by file or classpath URL");
        }
    }

    /**
     * 判断资源是否存在
     */
    public boolean exists() {
        if (filePath != null) {
            return Files.exists(filePath);
        } else if (classpathUrl != null) {
            // 对于 classpath 资源，尝试打开流来验证存在性
            // 这是必要的，因为 classpath URL 不一定支持 exists() 检查
            try {
                classpathUrl.openStream().close();
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * 获取资源路径（用于日志和标识）
     */
    public String getResourcePath() {
        return resourcePath;
    }

    /**
     * 获取资源唯一标识
     */
    public String getId() {
        if (filePath != null) {
            return filePath.toAbsolutePath().toString();
        } else if (classpathUrl != null) {
            // 对于 classpath 资源，返回原始传入的资源路径
            // 这与测试期望和行为一致
            return resourcePath;
        } else {
            return resourcePath;
        }
    }

    /**
     * 判断是否为文件系统资源
     */
    public boolean isFileSystemResource() {
        return filePath != null;
    }

    /**
     * 获取文件路径（如果是文件系统资源）
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * 获取文件名（用于解析时判断文件类型）
     */
    public String getFilename() {
        if (filePath != null) {
            return filePath.getFileName().toString();
        } else if (classpathUrl != null) {
            String path = classpathUrl.getPath();
            /* 移除开头的斜杠 */
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            /* 提取文件名 */
            int lastSlash = path.lastIndexOf('/');
            return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        } else {
            /* 从资源路径中提取作为备用方案 */
            return extractFilenameFromResourcePath();
        }
    }

    /**
     * 从资源路径中提取文件名（备用方案）
     */
    private String extractFilenameFromResourcePath() {
        if (resourcePath == null) {
            return "unknown";
        }

        /* 处理 classpath: 前缀 */
        if (resourcePath.startsWith("classpath:")) {
            String path = resourcePath.substring(10);
            int lastSlash = path.lastIndexOf('/');
            return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        }

        /* 处理文件系统路径 */
        int lastSlash = Math.max(resourcePath.lastIndexOf('/'), resourcePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? resourcePath.substring(lastSlash + 1) : resourcePath;
    }

    /**
     * 基于资源路径判断相等性
     * <p>
     * 两个 FileResource 被认为是相等的，如果它们引用相同的资源。
     * 对于文件系统资源，基于绝对路径；对于 Classpath 资源，基于原始资源路径。
     * </p>
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        FileResource other = (FileResource) obj;
        // 使用 resourcePath 作为相等性判断标准
        return Objects.equals(resourcePath, other.resourcePath);
    }

    /**
     * 基于资源路径生成哈希码
     * <p>
     * 使用 resourcePath 的哈希码，确保相等对象具有相同的哈希码。
     * </p>
     */
    @Override
    public int hashCode() {
        return resourcePath != null ? resourcePath.hashCode() : 0;
    }

    /**
     * 返回资源的字符串表示
     * <p>
     * 格式为 "FileResource{path='资源路径'}"，包含资源路径信息。
     * </p>
     */
    @Override
    public String toString() {
        return "FileResource{path='" + resourcePath + "'}";
    }
}