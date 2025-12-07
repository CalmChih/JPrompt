package com.chih.JPrompt.core.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * PromptObjectMapperFactory 单元测试
 *
 * 测试 ObjectMapper 工厂类的各种配置和线程安全性
 */
@DisplayName("PromptObjectMapperFactory 测试")
class PromptObjectMapperFactoryTest {

    @Test
    @DisplayName("YAML ObjectMapper 配置验证")
    void testCreateYamlMapper() throws JsonProcessingException {
        ObjectMapper mapper = PromptObjectMapperFactory.createYamlMapper();

        // 验证 YAML Factory
        assertThat(mapper.getFactory()).isInstanceOf(YAMLFactory.class);

        // 验证关键配置
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(mapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)).isFalse();
        assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();

        // 验证日期格式配置
        LocalDateTime dateTime = LocalDateTime.of(2025, 12, 7, 10, 30, 45);
        String serializedDate = mapper.writeValueAsString(dateTime);
        assertThat(serializedDate).contains("\"2025-12-07T10:30:45\"");
        assertThat(serializedDate).doesNotContain("1701945045000");
    }

    @Test
    @DisplayName("JSON ObjectMapper 配置验证")
    void testCreateJsonMapper() {
        ObjectMapper mapper = PromptObjectMapperFactory.createJsonMapper();

        // 验证标准 Factory（不是 YAML）
        assertThat(mapper.getFactory()).isNotInstanceOf(YAMLFactory.class);

        // 验证关键配置与 YAML mapper 一致
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(mapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)).isFalse();
        assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
    }

    @Test
    @DisplayName("宽松模式 ObjectMapper 配置验证")
    void testCreateLenientMapper() {
        ObjectMapper mapper = PromptObjectMapperFactory.createLenientMapper();

        // 验证基本配置继承
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isFalse();
        assertThat(mapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS)).isFalse();

        // 验证宽松配置
        assertThat(mapper.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)).isTrue();
        assertThat(mapper.isEnabled(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)).isTrue();
        assertThat(mapper.isEnabled(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)).isTrue();
        assertThat(mapper.isEnabled(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT)).isTrue();
    }

    @Test
    @DisplayName("YAML 解析功能测试")
    @SuppressWarnings("unchecked")
    void testYamlMapperParsing() throws IOException {
        ObjectMapper mapper = PromptObjectMapperFactory.createYamlMapper();

        String yamlContent = """
            prompt:
              id: greeting
              model: gpt-3.5-turbo
              template: Hello, {{name}}!
              temperature: 0.7
            """;

        Map<String, Object> result = mapper.readValue(yamlContent, Map.class);

        assertThat(result).containsKey("prompt");
        Map<String, Object> prompt = (Map<String, Object>) result.get("prompt");
        assertThat(prompt.get("id")).isEqualTo("greeting");
        assertThat(prompt.get("model")).isEqualTo("gpt-3.5-turbo");
        assertThat(prompt.get("template")).isEqualTo("Hello, {{name}}!");
        assertThat(prompt.get("temperature")).isEqualTo(0.7);
    }

    @Test
    @DisplayName("JSON 解析功能测试")
    @SuppressWarnings("unchecked")
    void testJsonMapperParsing() throws IOException {
        ObjectMapper mapper = PromptObjectMapperFactory.createJsonMapper();

        String jsonContent = """
            {
              "id": "welcome",
              "model": "gpt-4",
              "template": "Welcome to {{app_name}}!",
              "temperature": 0.8
            }
            """;

        Map<String, Object> result = mapper.readValue(jsonContent, Map.class);

        assertThat(result.get("id")).isEqualTo("welcome");
        assertThat(result.get("model")).isEqualTo("gpt-4");
        assertThat(result.get("template")).isEqualTo("Welcome to {{app_name}}!");
        assertThat(result.get("temperature")).isEqualTo(0.8);
    }

    @Test
    @DisplayName("YAML 序列化功能测试")
    void testYamlMapperSerialization() throws JsonProcessingException {
        ObjectMapper mapper = PromptObjectMapperFactory.createYamlMapper();

        Map<String, Object> data = Map.of(
            "id", "test",
            "model", "gpt-3.5-turbo",
            "template", "Hello, {{name}}!",
            "temperature", 0.7,
            "tags", new String[]{"greeting", "welcome"}
        );

        String yaml = mapper.writeValueAsString(data);

        // 验证序列化结果包含关键字段（YAML 格式会包含引号）
        assertThat(yaml).contains("id: \"test\"");
        assertThat(yaml).contains("model: \"gpt-3.5-turbo\"");
        assertThat(yaml).contains("template: \"Hello, {{name}}!\"");
        assertThat(yaml).contains("temperature: 0.7");
        assertThat(yaml).contains("- \"greeting\"");
        assertThat(yaml).contains("- \"welcome\"");
    }

    @Test
    @DisplayName("JSON 序列化功能测试")
    void testJsonMapperSerialization() throws JsonProcessingException {
        ObjectMapper mapper = PromptObjectMapperFactory.createJsonMapper();

        Map<String, Object> data = Map.of(
            "id", "test",
            "model", "gpt-3.5-turbo",
            "template", "Hello, {{name}}!",
            "temperature", 0.7,
            "tags", new String[]{"greeting", "welcome"}
        );

        String json = mapper.writeValueAsString(data);

        // 验证序列化结果包含关键字段
        assertThat(json).contains("\"id\":\"test\"");
        assertThat(json).contains("\"model\":\"gpt-3.5-turbo\"");
        assertThat(json).contains("\"template\":\"Hello, {{name}}!\"");
        assertThat(json).contains("\"temperature\":0.7");
        assertThat(json).contains("[\"greeting\",\"welcome\"]");
    }

    @Test
    @DisplayName("未知字段容忍性测试")
    @SuppressWarnings("unchecked")
    void testUnknownFieldsTolerance() throws IOException {
        ObjectMapper yamlMapper = PromptObjectMapperFactory.createYamlMapper();
        ObjectMapper jsonMapper = PromptObjectMapperFactory.createJsonMapper();

        // 测试 YAML
        String yamlWithUnknownFields = """
            id: test
            model: gpt-3.5-turbo
            template: Hello, {{name}}!
            unknown_field: "should not cause error"
            another_unknown:
              nested: "value"
              number: 123
            """;

        Map<String, Object> yamlResult = yamlMapper.readValue(yamlWithUnknownFields, Map.class);
        assertThat(yamlResult).containsKey("id");
        assertThat(yamlResult).containsKey("model");
        assertThat(yamlResult).containsKey("template");
        // 未知字段应该被忽略而不是导致错误
        assertThat(yamlResult).containsKey("unknown_field");

        // 测试 JSON
        String jsonWithUnknownFields = """
            {
              "id": "test",
              "model": "gpt-3.5-turbo",
              "template": "Hello, {{name}}!",
              "unknown_field": "should not cause error",
              "another_unknown": {
                "nested": "value",
                "number": 123
              }
            }
            """;

        Map<String, Object> jsonResult = jsonMapper.readValue(jsonWithUnknownFields, Map.class);
        assertThat(jsonResult).containsKey("id");
        assertThat(jsonResult).containsKey("model");
        assertThat(jsonResult).containsKey("template");
        // 未知字段应该被忽略而不是导致错误
        assertThat(jsonResult).containsKey("unknown_field");
    }

    @Test
    @DisplayName("宽松模式解析测试")
    @SuppressWarnings("unchecked")
    void testLenientMapperParsing() throws IOException {
        ObjectMapper mapper = PromptObjectMapperFactory.createLenientMapper();

        // 测试空字符串作为 null 对象
        String contentWithEmptyString = """
            id: test
            template: ""
            model: gpt-3.5-turbo
            """;

        Map<String, Object> result = mapper.readValue(contentWithEmptyString, Map.class);
        assertThat(result.get("id")).isEqualTo("test");
        assertThat(result.get("template")).isEqualTo(""); // 空字符串被保持为空字符串

        // 测试单值数组解包
        String contentWithSingleValueArray = """
            id: test
            tags: ["single_tag"]
            model: gpt-3.5-turbo
            """;

        Map<String, Object> result2 = mapper.readValue(contentWithSingleValueArray, Map.class);
        assertThat(result2.get("tags")).isEqualTo(java.util.List.of("single_tag")); // 单值数组保持为数组格式

        // 测试空数组作为 null 对象
        String contentWithEmptyArray = """
            id: test
            optional_field: []
            model: gpt-3.5-turbo
            """;

        Map<String, Object> result3 = mapper.readValue(contentWithEmptyArray, Map.class);
        assertThat(result3.get("id")).isEqualTo("test");
        assertThat(result3.get("optional_field")).isEqualTo(java.util.List.of()); // 空数组保持为空列表
    }

    @Test
    @DisplayName("空对象序列化测试")
    void testEmptyObjectSerialization() throws JsonProcessingException {
        ObjectMapper yamlMapper = PromptObjectMapperFactory.createYamlMapper();
        ObjectMapper jsonMapper = PromptObjectMapperFactory.createJsonMapper();

        Map<String, Object> emptyMap = new java.util.HashMap<>();

        // YAML 序列化空对象应该不抛出异常（YAML 格式包含文档分隔符和换行符）
        String yamlResult = yamlMapper.writeValueAsString(emptyMap);
        assertThat(yamlResult).isEqualTo("--- {}\n");

        // JSON 序列化空对象应该不抛出异常
        String jsonResult = jsonMapper.writeValueAsString(emptyMap);
        assertThat(jsonResult).isEqualTo("{}");
    }

    @Test
    @DisplayName("线程安全性测试")
    @SuppressWarnings("unchecked")
    void testMapperThreadSafety() throws InterruptedException {
        int threadCount = 10;
        int iterationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];
        boolean[] success = new boolean[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        // 并发创建 mapper
                        ObjectMapper yamlMapper = PromptObjectMapperFactory.createYamlMapper();
                        ObjectMapper jsonMapper = PromptObjectMapperFactory.createJsonMapper();
                        ObjectMapper lenientMapper = PromptObjectMapperFactory.createLenientMapper();

                        // 并发使用 mapper 进行简单操作
                        String testContent = "{\"id\":\"test\",\"template\":\"Hello\"}";
                        Map<String, Object> result1 = yamlMapper.readValue(testContent, Map.class);
                        Map<String, Object> result2 = jsonMapper.readValue(testContent, Map.class);
                        Map<String, Object> result3 = lenientMapper.readValue(testContent, Map.class);

                        // 验证结果一致性
                        assertThat(result1.get("id")).isEqualTo("test");
                        assertThat(result2.get("id")).isEqualTo("test");
                        assertThat(result3.get("id")).isEqualTo("test");
                    }
                    success[threadIndex] = true;
                } catch (Exception e) {
                    success[threadIndex] = false;
                    e.printStackTrace();
                }
            });
        }

        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join(10000); // 10秒超时
        }

        // 验证所有线程都成功完成
        for (int i = 0; i < threadCount; i++) {
            assertThat(success[i]).isTrue();
        }
    }

    @Test
    @DisplayName("Mapper 实例唯一性测试")
    void testMapperInstanceUniqueness() {
        // 工厂方法应该每次返回新的实例，而不是单例
        ObjectMapper mapper1 = PromptObjectMapperFactory.createYamlMapper();
        ObjectMapper mapper2 = PromptObjectMapperFactory.createYamlMapper();

        assertThat(mapper1).isNotSameAs(mapper2);
        // 但是配置应该相同
        assertThat(mapper1.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
            .isEqualTo(mapper2.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    @Test
    @DisplayName("日期序列化格式测试")
    void testDateFormatSerialization() throws JsonProcessingException {
        ObjectMapper yamlMapper = PromptObjectMapperFactory.createYamlMapper();
        ObjectMapper jsonMapper = PromptObjectMapperFactory.createJsonMapper();

        LocalDateTime dateTime = LocalDateTime.of(2025, 12, 7, 14, 30, 45, 123456789);

        // YAML 日期序列化
        String yamlResult = yamlMapper.writeValueAsString(dateTime);
        assertThat(yamlResult).contains("\"2025-12-07T14:30:45.123456789\"");

        // JSON 日期序列化
        String jsonResult = jsonMapper.writeValueAsString(dateTime);
        assertThat(jsonResult).contains("\"2025-12-07T14:30:45.123456789\"");

        // 确保不使用时间戳格式
        assertThat(yamlResult).doesNotContain("1701945045");
        assertThat(jsonResult).doesNotContain("1701945045");
    }

    @Test
    @DisplayName("复杂对象序列化测试")
    @SuppressWarnings("unchecked")
    void testComplexObjectSerialization() throws JsonProcessingException {
        ObjectMapper mapper = PromptObjectMapperFactory.createYamlMapper();

        // 创建包含嵌套结构的复杂对象
        Map<String, Object> complexObject = Map.of(
            "metadata", Map.of(
                "version", "1.0",
                "created_at", "2025-12-07T10:00:00",
                "author", "test-user"
            ),
            "config", Map.of(
                "model", "gpt-4",
                "temperature", 0.7,
                "max_tokens", 2048,
                "stream", false
            ),
            "prompts", Map.of(
                "system", "You are a helpful assistant.",
                "user", "Hello, {{name}}!"
            )
        );

        String yamlResult = mapper.writeValueAsString(complexObject);

        // 验证复杂结构的序列化（YAML 格式会包含引号）
        assertThat(yamlResult).contains("version: \"1.0\"");
        assertThat(yamlResult).contains("model: \"gpt-4\"");
        assertThat(yamlResult).contains("system: \"You are a helpful assistant.\"");

        // 验证可以反序列化回来
        Map<String, Object> deserialized = mapper.readValue(yamlResult, Map.class);
        Map<String, Object> metadata = (Map<String, Object>) deserialized.get("metadata");
        assertThat(metadata.get("version")).isEqualTo("1.0");
    }
}