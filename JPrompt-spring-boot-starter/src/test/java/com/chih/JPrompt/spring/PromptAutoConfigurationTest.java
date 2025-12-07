package com.chih.JPrompt.spring;

import com.chih.JPrompt.core.engine.PromptManager;
import com.chih.JPrompt.core.engine.PromptMapperFactory;
import com.chih.JPrompt.core.spi.PromptSource;
import com.chih.JPrompt.core.spi.TemplateEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.*;

/**
 * PromptAutoConfiguration 单元测试
 *
 * 测试 Spring Boot 自动配置功能，包括：
 * - 基本配置逻辑验证
 * - Bean 创建逻辑验证
 * - 配置属性验证
 */
@DisplayName("PromptAutoConfiguration 测试")
class PromptAutoConfigurationTest {

    @Test
    @DisplayName("JPromptProperties 默认配置应该正确")
    void testJPromptPropertiesDefaults() {
        JPromptProperties properties = new JPromptProperties();

        /* 验证默认位置配置 */
        assertThat(properties.getLocations()).isNotEmpty();
        assertThat(properties.getLocations()).contains(
            "classpath*:prompts/**/*.yaml",
            "classpath*:prompts/**/*.yml",
            "file:./prompts.yaml",
            "file:./prompts.yml",
            "file:./prompts/*.yaml",
            "file:./prompts/*.yml",
            "classpath*:prompts/**/*.md"
        );

        /* 验证默认防抖延迟 */
        assertThat(properties.getDebounceMillis()).isEqualTo(500);
    }

    @Test
    @DisplayName("JPromptProperties 配置设置和获取应该正确")
    void testJPromptPropertiesSettersAndGetters() {
        JPromptProperties properties = new JPromptProperties();

        /* 测试 locations 设置 */
        properties.setLocations(java.util.List.of("classpath:/custom/", "file:/custom/"));
        assertThat(properties.getLocations()).containsExactly("classpath:/custom/", "file:/custom/");

        /* 测试 debounceMillis 设置 */
        properties.setDebounceMillis(2000);
        assertThat(properties.getDebounceMillis()).isEqualTo(2000);
    }

    @Test
    @DisplayName("SpringResourcePromptSource 构造函数应该正确工作")
    void testSpringResourcePromptSourceConstructor() throws Exception {
        java.util.List<String> locations = java.util.List.of("classpath:/test/");
        long debounceDelay = 1000;

        /* 测试基本构造函数 */
        SpringResourcePromptSource source = new SpringResourcePromptSource(
            locations, debounceDelay, null, null
        );

        assertThat(source).isNotNull();

        /* 清理资源 */
        source.close();
    }

    @Test
    @DisplayName("SpringResourcePromptSource 基本功能应该正常")
    void testSpringResourcePromptSourceBasicFunctionality() throws Exception {
        java.util.List<String> locations = java.util.List.of("classpath:/nonexistent/");

        SpringResourcePromptSource source = new SpringResourcePromptSource(
            locations, 500, null, null
        );

        try {
            /* 测试 loadAll 方法 */
            java.util.Map<String, ?> prompts = source.loadAll();
            assertThat(prompts).isNotNull();
            assertThat(prompts).isEmpty(); /* 因为路径不存在 */

            /* 测试 load 方法 */
            Object prompt = source.load("nonexistent");
            assertThat(prompt).isNull();

            /* 测试 getLoadErrors 方法 */
            java.util.Map<String, Throwable> errors = source.getLoadErrors();
            assertThat(errors).isNotNull();
        } finally {
            source.close();
        }
    }

    @Test
    @DisplayName("JPromptProperties 构造函数应该初始化默认值")
    void testJPromptPropertiesConstructor() {
        JPromptProperties properties = new JPromptProperties();

        /* 验证构造函数正确初始化了默认值 */
        assertThat(properties.getLocations()).isNotEmpty();
        assertThat(properties.getDebounceMillis()).isEqualTo(500);
    }

    @Test
    @DisplayName("配置类注解应该正确")
    void testConfigurationAnnotations() {
        /* 验证 PromptAutoConfiguration 类的注解 */
        Class<PromptAutoConfiguration> configClass = PromptAutoConfiguration.class;

        assertThat(configClass.isAnnotationPresent(org.springframework.context.annotation.Configuration.class))
            .isTrue();
        assertThat(configClass.isAnnotationPresent(org.springframework.boot.context.properties.EnableConfigurationProperties.class))
            .isTrue();
    }

    @Test
    @DisplayName("JPromptProperties 注解应该正确")
    void testPropertiesAnnotations() {
        /* 验证 JPromptProperties 类的注解 */
        Class<JPromptProperties> propertiesClass = JPromptProperties.class;

        assertThat(propertiesClass.isAnnotationPresent(org.springframework.boot.context.properties.ConfigurationProperties.class))
            .isTrue();

        /* 验证注解属性 */
        org.springframework.boot.context.properties.ConfigurationProperties annotation =
            propertiesClass.getAnnotation(org.springframework.boot.context.properties.ConfigurationProperties.class);
        assertThat(annotation.prefix()).isEqualTo("j-prompt");
    }

    @Test
    @DisplayName("线程池配置方法存在性验证")
    void testThreadPoolConfigurationMethods() {
        Class<PromptAutoConfiguration> configClass = PromptAutoConfiguration.class;

        /* 验证线程池配置方法存在 */
        assertThat(configClass.getMethods()).anyMatch(method ->
            method.getName().equals("jPromptWatcherExecutor") &&
            method.getReturnType().equals(ExecutorService.class) &&
            method.isAnnotationPresent(org.springframework.context.annotation.Bean.class)
        );

        assertThat(configClass.getMethods()).anyMatch(method ->
            method.getName().equals("jPromptDebounceExecutor") &&
            method.getReturnType().equals(ScheduledExecutorService.class) &&
            method.isAnnotationPresent(org.springframework.context.annotation.Bean.class)
        );
    }

    @Test
    @DisplayName("核心 Bean 配置方法存在性验证")
    void testCoreBeanConfigurationMethods() {
        Class<PromptAutoConfiguration> configClass = PromptAutoConfiguration.class;

        /* 验证核心 Bean 配置方法存在 */
        assertThat(configClass.getMethods()).anyMatch(method ->
            method.getName().equals("promptSource") &&
            method.getReturnType().equals(PromptSource.class) &&
            method.isAnnotationPresent(org.springframework.context.annotation.Bean.class)
        );

        assertThat(configClass.getMethods()).anyMatch(method ->
            method.getName().equals("templateEngine") &&
            method.getReturnType().equals(TemplateEngine.class) &&
            method.isAnnotationPresent(org.springframework.context.annotation.Bean.class)
        );

        assertThat(configClass.getMethods()).anyMatch(method ->
            method.getName().equals("promptManager") &&
            method.getReturnType().equals(PromptManager.class) &&
            method.isAnnotationPresent(org.springframework.context.annotation.Bean.class)
        );

        assertThat(configClass.getMethods()).anyMatch(method ->
            method.getName().equals("promptMapperFactory") &&
            method.getReturnType().equals(PromptMapperFactory.class) &&
            method.isAnnotationPresent(org.springframework.context.annotation.Bean.class)
        );
    }

    @Test
    @DisplayName("条件装配注解验证")
    void testConditionalAnnotations() {
        Class<PromptAutoConfiguration> configClass = PromptAutoConfiguration.class;

        /* 查找带有 @ConditionalOnMissingBean 的方法 */
        boolean hasConditionalOnMissingBean = java.util.Arrays.stream(configClass.getMethods())
            .anyMatch(method ->
                method.isAnnotationPresent(org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean.class)
            );
        assertThat(hasConditionalOnMissingBean).isTrue();
    }

    @Test
    @DisplayName("内部配置类验证")
    void testInnerConfigurationClasses() {
        Class<PromptAutoConfiguration> configClass = PromptAutoConfiguration.class;

        /* 验证内部配置类存在 */
        Class<?>[] innerClasses = configClass.getDeclaredClasses();

        boolean hasMetricsConfiguration = java.util.Arrays.stream(innerClasses)
            .anyMatch(clazz -> clazz.getSimpleName().equals("MetricsConfiguration"));

        boolean hasHealthCheckConfiguration = java.util.Arrays.stream(innerClasses)
            .anyMatch(clazz -> clazz.getSimpleName().equals("HealthCheckConfiguration"));

        assertThat(hasMetricsConfiguration).isTrue();
        assertThat(hasHealthCheckConfiguration).isTrue();
    }

    @Test
    @DisplayName("SpringResourcePromptSource 继承关系验证")
    void testSpringResourcePromptSourceInheritance() {
        /* 验证 SpringResourcePromptSource 的继承关系 */
        Class<?>[] interfaces = SpringResourcePromptSource.class.getInterfaces();

        boolean hasAutoCloseable = java.util.Arrays.stream(interfaces)
            .anyMatch(clazz -> clazz.equals(java.lang.AutoCloseable.class));

        boolean hasDisposableBean = java.util.Arrays.stream(interfaces)
            .anyMatch(clazz -> clazz.equals(org.springframework.beans.factory.DisposableBean.class));

        assertThat(hasAutoCloseable).isTrue();
        assertThat(hasDisposableBean).isTrue();

        /* 验证父类 */
        assertThat(SpringResourcePromptSource.class.getSuperclass().getSimpleName())
            .isEqualTo("AbstractIndexBasedPromptSource");
    }
}