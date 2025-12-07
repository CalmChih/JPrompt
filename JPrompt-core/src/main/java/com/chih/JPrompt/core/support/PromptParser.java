package com.chih.JPrompt.core.support;

import com.chih.JPrompt.core.domain.PromptMeta;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一的 Prompt 解析器
 * <p>
 * 提供多格式 Prompt 文件的统一解析能力，支持：
 * 1. YAML 格式：支持单个文件定义多个 Prompt
 * 2. JSON 格式：YAML 的 JSON 变体，功能相同
 * 3. Markdown 格式：支持 FrontMatter 语法，一个文件一个 Prompt
 * 4. UTF-8 BOM 处理和换行符标准化
 * 5. 线程安全的解析逻辑
 * </p>
 *
 * <h3>支持格式示例：</h3>
 * <pre>{@code
 * # YAML 格式（支持多个 Prompt）
 * greeting:
 *   id: greeting
 *   model: gpt-3.5-turbo
 *   template: Hello, {{name}}!
 *
 * farewell:
 *   id: farewell
 *   model: gpt-4
 *   template: Goodbye, {{name}}!
 *
 * # Markdown 格式（FrontMatter）
 * ---
 * id: greeting
 * model: gpt-3.5-turbo
 * temperature: 0.7
 * ---
 * Hello, {{name}}!
 * </pre>
 *
 * @author lizhiyuan
 * @since 2025/12/07
 */
public class PromptParser {

    private static final Logger log = LoggerFactory.getLogger(PromptParser.class);

    /**
     * FrontMatter 正则表达式
     * <p>
     * 匹配 Markdown 文件开头的 YAML 元数据：
     * ^---\s*\n      - 文件开头，三个破折号，可能包含空白字符
     * (.*?)          - 捕获组1：YAML 内容（非贪婪模式）
     * \n---\s*\n      - YAML 结束标记，三个破折号
     * (.*)           - 捕获组2：模板内容（直到文件结束）
     * $              - 字符串结束
     * Pattern.DOTALL - 让 . 匹配换行符，支持多行 YAML
     * </p>
     */
    private static final Pattern FRONT_MATTER_PATTERN =
            Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL);

    /**
     * 支持的文件扩展名集合
     * <p>
     * 使用 Set 提供O(1)的查找性能，避免复杂的正则匹配
     * 扩展名统一使用小写，确保大小写不敏感的匹配
     * </p>
     */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
        ".yaml",
        ".yml",
        ".json",
        ".md"
    );

    /**
     * 最大文件大小限制（10MB）
     * <p>
     * 防止解析过大的文件导致 OOM，提供内存保护。
     * Prompt 文件通常很小（几KB到几十KB），10MB 是一个安全的上限。
     * </p>
     */
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * 线程安全的 YAML 映射器
     * <p>
     * 使用工厂方法创建，确保配置一致性。
     * ObjectMapper 实例是线程安全的，可以全局共享。
     * </p>
     */
    private static final ObjectMapper YAML_MAPPER =
        PromptObjectMapperFactory.createYamlMapper();

    /**
     * 判断文件是否为支持的 Prompt 格式
     *
     * 根据文件扩展名判断是否为支持的 Prompt 文件格式。
     * 该方法是大小写不敏感的，会自动转换为小写进行匹配。
     *
     * @param filename 文件名，包含扩展名
     * @return 如果是支持的格式返回 true，否则返回 false
     */
    public static boolean isSupportedFile(String filename) {
        if (filename == null) {
            return false;
        }

        // 转换为小写，确保大小写不敏感匹配
        String lowerFilename = filename.toLowerCase();
        return SUPPORTED_EXTENSIONS.stream()
            .anyMatch(lowerFilename::endsWith);
    }

    /**
     * 解析输入流为 Prompt 集合（统一入口）
     *
     * 根据文件扩展名自动选择解析策略：
     * - YAML/JSON: 直接解析为 Map<String, PromptMeta>
     * - Markdown: 解析 FrontMatter + 模板内容
     *
     * @param is 输入流，调用方负责关闭
     * @param filename 文件名（用于判断文件类型和生成默认 ID）
     * @return Prompt 映射集合，Key 为 Prompt ID，Value 为 Prompt 元数据
     * @throws IOException 解析异常，包含详细的错误信息和上下文
     * @throws IllegalArgumentException 输入参数无效时抛出
     */
    public static Map<String, PromptMeta> parse(InputStream is, String filename) throws IOException {
        if (is == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }

        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }

        try {
            // 根据文件扩展名选择解析策略
            if (isYamlFile(filename) || isJsonFile(filename)) {
                return parseYamlOrJson(is);
            } else if (isMarkdownFile(filename)) {
                return parseMarkdown(is, filename);
            }

            // 不支持的文件格式，记录警告并返回空集合
            log.warn("Unsupported file type: {}. Supported types: {}", filename, SUPPORTED_EXTENSIONS);
            return Collections.emptyMap();

        } catch (Exception e) {
            // 记录详细的错误信息，包括文件名和异常堆栈
            log.error("Failed to parse prompt file: {}. Error: {}", filename, e.getMessage(), e);
            throw new IOException("Failed to parse prompt file: " + filename, e);
        }
    }

    /**
     * 解析 YAML 或 JSON 格式的文件
     *
     * 使用 Jackson ObjectMapper 解析文件内容。
     * YAML 和 JSON 在 Jackson 中使用相同的 TypeReference，
     * 因此可以统一处理。
     *
     * @param is 输入流
     * @return Prompt 映射集合，支持一个文件包含多个 Prompt
     * @throws IOException 解析异常
     */
    private static Map<String, PromptMeta> parseYamlOrJson(InputStream is) throws IOException {
        // 使用 TypeReference 确保类型安全，避免 Map<String, Object> 的类型转换问题
        return YAML_MAPPER.readValue(is, new TypeReference<Map<String, PromptMeta>>() {});
    }

    /**
     * 解析 Markdown 文件（支持 FrontMatter，带内存保护）
     *
     * Markdown 文件支持两种格式：
     * 1. 带 FrontMatter：以 --- 开头的 YAML 元数据 + 模板内容
     * 2. 纯模板：整个文件作为模板内容，使用文件名作为 ID
     *
     * @param is 输入流
     * @param filename 文件名（用于生成默认 ID）
     * @return 只包含一个 Prompt 的映射集合（Key 为 Prompt ID）
     * @throws IOException 读取异常或文件过大异常
     */
    private static Map<String, PromptMeta> parseMarkdown(InputStream is, String filename) throws IOException {
        // 读取整个流内容到字符串，带内存保护
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192]; // 8KB 缓冲区
        int nRead;
        int totalBytes = 0;

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            totalBytes += nRead;

            // 内存保护：检查文件大小，防止 OOM
            if (totalBytes > MAX_FILE_SIZE) {
                throw new IOException(String.format(
                    "File '%s' is too large (%d bytes). Maximum allowed size: %d bytes. " +
                    "Prompt files should be relatively small. Please check if you selected the correct file.",
                    filename, totalBytes, MAX_FILE_SIZE));
            }

            buffer.write(data, 0, nRead);
        }
        String content = buffer.toString(StandardCharsets.UTF_8);

        // 标准化内容：移除 BOM 头，统一换行符
        content = normalizeContent(content);

        // 初始化 PromptMeta 对象
        PromptMeta meta = new PromptMeta();
        String templateBody = content;

        // 尝试解析 FrontMatter
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(content);
        if (matcher.find()) {
            // 匹配到 FrontMatter，提取 YAML 部分和模板内容
            String yamlPart = matcher.group(1);      // FrontMatter YAML 内容
            templateBody = matcher.group(2).trim();  // 模板主体内容

            // 解析 FrontMatter 中的 YAML 元数据
            if (yamlPart != null && !yamlPart.trim().isEmpty()) {
                try {
                    meta = YAML_MAPPER.readValue(yamlPart, PromptMeta.class);
                } catch (Exception e) {
                    // FrontMatter 解析失败时，使用空元数据并记录警告
                    log.warn("Failed to parse FrontMatter YAML in file: {}, using empty meta. Error: {}",
                            filename, e.getMessage(), e);
                    meta = new PromptMeta();
                }
            }
        }

        // 设置模板内容
        meta.setTemplate(templateBody);

        // ID 兜底策略：如果 FrontMatter 中没有定义 ID，使用文件名生成
        if (meta.getId() == null || meta.getId().trim().isEmpty()) {
            meta.setId(generateIdFromFilename(filename));
        }

        // 校验 Prompt 元数据的完整性
        meta.validate();

        // Markdown 文件只包含一个 Prompt，返回单元素映射
        return Collections.singletonMap(meta.getId(), meta);
    }

    /**
     * 标准化内容：移除 BOM 头，统一换行符
     *
     * 处理常见的文本编码问题：
     * 1. UTF-8 BOM (Byte Order Mark) 移除：\uFEFF
     * 2. 换行符统一：Windows (\r\n) -> Unix (\n)
     * 3. 空值处理：null 返回空字符串
     *
     * @param content 原始内容字符串
     * @return 标准化后的内容字符串
     */
    public static String normalizeContent(String content) {
        if (content == null) {
            return "";
        }

        /* 移除 UTF-8 BOM 头 */
        /* BOM 是某些 Windows 编辑器添加的标记，会影响解析 */
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        /* 统一换行符：将 Windows 风格的换行符替换为 Unix 风格 */
        /* 这样可以确保正则表达式匹配的一致性 */
        content = content.replace("\r\n", "\n");

        return content;
    }

    /**
     * 从文件名生成 Prompt ID
     *
     * 当 FrontMatter 中没有定义 ID 时，使用文件名作为兜底策略。
     * 生成规则：
     * 1. 移除路径部分，只保留文件名
     * 2. 移除文件扩展名
     * 3. 使用剩余部分作为 ID
     *
     * 示例：
     * - "prompts/greeting.yaml" -> "greeting"
     * - "C:\prompts\hello.md" -> "hello"
     * - "farewell.yml" -> "farewell"
     *
     * @param filename 完整的文件路径和名称
     * @return 生成的 Prompt ID，无效输入时返回 "unknown"
     */
    private static String generateIdFromFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "unknown";
        }

        /* 移除路径部分，只保留文件名 */
        /* 支持 Unix (/) 和 Windows (\) 路径分隔符 */
        int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String name = lastSlash >= 0 ? filename.substring(lastSlash + 1) : filename;

        /* 移除文件扩展名 */
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            name = name.substring(0, lastDot);
        }

        /* 清理文件名：移除特殊字符，替换为连字符 */
        /* 这样生成的 ID 更符合命名规范 */
        return name.replaceAll("[^a-zA-Z0-9_-]", "-");
    }

    // === 文件类型判断方法 ===

    /**
     * 判断是否为 YAML 文件
     *
     * @param filename 文件名
     * @return 如果是 .yaml 或 .yml 文件返回 true
     */
    private static boolean isYamlFile(String filename) {
        String lowerFilename = filename.toLowerCase();
        return lowerFilename.endsWith(".yaml") || lowerFilename.endsWith(".yml");
    }

    /**
     * 判断是否为 JSON 文件
     *
     * @param filename 文件名
     * @return 如果是 .json 文件返回 true
     */
    private static boolean isJsonFile(String filename) {
        return filename.toLowerCase().endsWith(".json");
    }

    /**
     * 判断是否为 Markdown 文件
     *
     * @param filename 文件名
     * @return 如果是 .md 文件返回 true
     */
    private static boolean isMarkdownFile(String filename) {
        return filename.toLowerCase().endsWith(".md");
    }
}