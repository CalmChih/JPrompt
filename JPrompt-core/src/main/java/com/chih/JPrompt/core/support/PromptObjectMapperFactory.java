package com.chih.JPrompt.core.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Prompt ObjectMapper 工厂类。
 * <p>
 * 提供统一的 Jackson ObjectMapper 配置，确保各模块使用相同的序列化设置。
 * 工厂类模式的优势：
 * 1. 配置集中管理，避免重复代码
 * 2. 确保所有 ObjectMapper 使用一致的配置策略
 * 3. 便于维护和调整序列化行为
 * 4. 支持 YAML 和 JSON 两种格式的统一处理
 * </p>
 *
 * <h3>配置策略说明：</h3>
 * <ul>
 *   <li>FAIL_ON_UNKNOWN_PROPERTIES: false - 容忍未知字段，提高兼容性</li>
 *   <li>FAIL_ON_EMPTY_BEANS: false - 不序列化空对象，避免冗余数据</li>
 *   <li>WRITE_DATES_AS_TIMESTAMPS: disabled - 使用 ISO-8601 格式，提高可读性</li>
 * </ul>
 *
 * @author lizhiyuan
 * @since 2025/12/07
 */
public class PromptObjectMapperFactory {

    /**
     * 创建用于 YAML 解析的 ObjectMapper。
     *
     * 使用 YAMLFactory 创建专用于 YAML 格式的映射器。
     * 适用于解析 Prompt 配置文件、FrontMatter 内容等场景。
     *
     * <h3>配置特点：</h3>
     * <ul>
     *   <li>容错性：忽略未知字段，支持向前兼容</li>
     *   <li>紧凑性：不序列化空对象和空属性</li>
     *   <li>可读性：日期使用 ISO-8601 格式而非时间戳</li>
     * </ul>
     *
     * @return 配置好的 YAML ObjectMapper，线程安全可重用
     */
    public static ObjectMapper createYamlMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        /* 注册 Java 8 时间模块，支持 LocalDateTime 等现代时间类型 */
        mapper.registerModule(new JavaTimeModule());

        /* 反序列化配置：遇到未知属性时不抛出异常，提高向前兼容性 */
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        /* 序列化配置：空对象不抛出异常，避免运行时错误 */
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        /* 日期格式配置：禁用时间戳格式，使用 ISO-8601 标准格式 */
        /* 例如：2025-12-07T10:30:45 而不是 1701945045000 */
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }

    /**
     * 创建用于 JSON 解析的 ObjectMapper。
     *
     * 使用标准 JSON factory 创建专用于 JSON 格式的映射器。
     * 与 YAML 映射器保持一致的配置策略，确保行为统一。
     *
     * <h3>使用场景：</h3>
     * <ul>
     *   <li>解析 JSON 格式的 Prompt 配置文件</li>
     *   <li>与其他 JSON API 进行数据交换</li>
     *   <li>生成 JSON 格式的输出报告</li>
     * </ul>
     *
     * @return 配置好的 JSON ObjectMapper，线程安全可重用
     */
    public static ObjectMapper createJsonMapper() {
        ObjectMapper mapper = new ObjectMapper();

        /* 注册 Java 8 时间模块，支持 LocalDateTime 等现代时间类型 */
        mapper.registerModule(new JavaTimeModule());

        /* 保持与 YAML 映射器一致的配置策略 */
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }

    /**
     * 创建宽松模式的 ObjectMapper（用于用户输入解析）
     *
     * 在标准配置基础上增加更宽松的解析策略，适用于处理用户输入的场景。
     * 宽松模式能够容忍更多格式不规范的情况，提供更好的用户体验。
     *
     * <h3>宽松配置说明：</h3>
     * <ul>
     *   <li>ACCEPT_EMPTY_STRING_AS_NULL_OBJECT: 空字符串视为 null 对象</li>
     *   <li>ACCEPT_SINGLE_VALUE_AS_ARRAY: 单值可接受为数组格式</li>
     *   <li>UNWRAP_SINGLE_VALUE_ARRAYS: 自动解包单值数组</li>
     *   <li>ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT: 空数组视为 null 对象</li>
     * </ul>
     *
     * <h3>典型使用场景：</h3>
     * <pre>{@code
     * // 以下格式都能被宽松模式正确解析：
     * template: "Hello {{name}}"           // 标准字符串
     * template: ""                          // 空字符串 -> null
     * template: ["Hello {{name}}"]          // 单值数组 -> 解包为字符串
     * template: []                          // 空数组 -> null
     * }</pre>
     *
     * @return 配置为宽松模式的 YAML ObjectMapper，线程安全可重用
     */
    public static ObjectMapper createLenientMapper() {
        ObjectMapper mapper = createYamlMapper();

        /* 配置更宽松的反序列化策略，提高用户输入的容错性 */
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        mapper.configure(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS, true);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);

        return mapper;
    }
}