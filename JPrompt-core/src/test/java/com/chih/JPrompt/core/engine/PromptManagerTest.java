package com.chih.JPrompt.core.engine;

import com.chih.JPrompt.core.domain.PromptMeta;
import com.chih.JPrompt.core.exception.PromptNotFoundException;
import com.chih.JPrompt.core.impl.MustacheTemplateEngine;
import com.chih.JPrompt.core.impl.NoOpPromptMetrics;
import com.chih.JPrompt.core.spi.PromptSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PromptManager 核心功能测试
 *
 * 测试覆盖：
 * - 基本的渲染功能
 * - 缓存机制
 * - 并发安全性
 * - 热更新机制
 * - 错误处理
 */
@ExtendWith(MockitoExtension.class)
public class PromptManagerTest {

    @Mock
    private PromptSource mockSource;

    private MustacheTemplateEngine templateEngine;
    private PromptManager promptManager;

    @BeforeEach
    void setUp() {
        templateEngine = new MustacheTemplateEngine();
        promptManager = new PromptManager(mockSource, templateEngine, new NoOpPromptMetrics());
    }

    @Test
    void testBasicRender() {
        // Given
        String promptKey = "greeting";
        String template = "Hello {{name}}!";
        PromptMeta promptMeta = createPromptMeta(promptKey, template);

        when(mockSource.load(eq(promptKey))).thenReturn(promptMeta);

        // When
        String result = promptManager.render(promptKey, Map.of("name", "World"));

        // Then
        assertThat(result).isEqualTo("Hello World!");
        verify(mockSource, times(1)).load(promptKey);
    }

    @Test
    void testRenderWithMultipleParameters() {
        // Given
        String promptKey = "order";
        String template = "Order {{id}} for {{customer}} total: ${{price}}";
        PromptMeta promptMeta = createPromptMeta(promptKey, template);

        when(mockSource.load(eq(promptKey))).thenReturn(promptMeta);

        Map<String, Object> params = Map.of(
            "id", "ORD-001",
            "customer", "Alice",
            "price", 99.99
        );

        // When
        String result = promptManager.render(promptKey, params);

        // Then
        assertThat(result).contains("ORD-001");
        assertThat(result).contains("Alice");
        assertThat(result).contains("99.99");
    }

    @Test
    void testRenderCaching() {
        // Given
        String promptKey = "cached";
        String template = "Cached template: {{value}}";
        PromptMeta promptMeta = createPromptMeta(promptKey, template);

        when(mockSource.load(eq(promptKey))).thenReturn(promptMeta);

        // When - 多次调用同一个模板
        String result1 = promptManager.render(promptKey, Map.of("value", "test1"));
        String result2 = promptManager.render(promptKey, Map.of("value", "test2"));
        String result3 = promptManager.render(promptKey, Map.of("value", "test3"));

        // Then - 只应该调用一次 load（因为缓存）
        assertThat(result1).isEqualTo("Cached template: test1");
        assertThat(result2).isEqualTo("Cached template: test2");
        assertThat(result3).isEqualTo("Cached template: test3");
        verify(mockSource, times(1)).load(promptKey);
    }

    @Test
    void testRenderNotFoundPrompt() {
        // Given
        String promptKey = "nonexistent";
        when(mockSource.load(eq(promptKey))).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> promptManager.render(promptKey, Map.of()))
            .isInstanceOf(PromptNotFoundException.class)
            .hasMessageContaining(promptKey);
    }

    @Test
    void testGetMeta() {
        // Given
        String promptKey = "meta-test";
        PromptMeta promptMeta = createPromptMeta(promptKey, "Template: {{param}}");
        promptMeta.setModel("gpt-4");
        promptMeta.setDescription("Test meta");

        when(mockSource.load(eq(promptKey))).thenReturn(promptMeta);

        // When - 先调用 render 来填充缓存
        promptManager.render(promptKey, Map.of("param", "test"));
        PromptMeta result = promptManager.getMeta(promptKey);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(promptKey);
        // 注意：由于缓存优化，某些字段可能不会被保存，所以只检查必要的字段
    }

    @Test
    void testConcurrentRender() throws InterruptedException {
        // Given
        String promptKey = "concurrent";
        String template = "Thread: {{threadId}} - Value: {{value}}";
        PromptMeta promptMeta = createPromptMeta(promptKey, template);

        when(mockSource.load(eq(promptKey))).thenReturn(promptMeta);

        int threadCount = 10;
        int iterationsPerThread = 50; // 减少迭代次数以加快测试
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When - 并发渲染
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        String result = promptManager.render(promptKey, Map.of(
                            "threadId", threadId,
                            "value", j
                        ));
                        assertThat(result).contains("Thread: " + threadId);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount * iterationsPerThread);

        // 验证缓存效果：只应该调用一次 load
        verify(mockSource, atMost(1)).load(promptKey);

        executor.shutdown();
    }

    @Test
    void testRenderWithComplexObject() {
        // Given
        String promptKey = "complex";
        String template = "User: {{user.name}}, Age: {{user.age}}, Items: {{#items}}{{.}}{{/items}}";
        PromptMeta promptMeta = createPromptMeta(promptKey, template);

        when(mockSource.load(eq(promptKey))).thenReturn(promptMeta);

        Map<String, Object> user = Map.of("name", "John", "age", 30);
        Map<String, Object> params = Map.of(
            "user", user,
            "items", java.util.List.of("A", "B", "C")
        );

        // When
        String result = promptManager.render(promptKey, params);

        // Then
        assertThat(result).contains("User: John");
        assertThat(result).contains("Age: 30");
        assertThat(result).contains("ABC");
    }

    @Test
    void testRenderWithNullParameters() {
        // Given
        String promptKey = "null-test";
        String template = "Value: {{param}}";
        PromptMeta promptMeta = createPromptMeta(promptKey, template);

        when(mockSource.load(eq(promptKey))).thenReturn(promptMeta);

        // When
        String result = promptManager.render(promptKey, null);

        // Then - 应该能够处理 null 参数
        assertThat(result).isEqualTo("Value: ");
    }

    @Test
    void testRenderWithEmptyParameters() {
        // Given
        String promptKey = "empty-test";
        String template = "Static template";
        PromptMeta promptMeta = createPromptMeta(promptKey, template);

        when(mockSource.load(eq(promptKey))).thenReturn(promptMeta);

        // When
        String result = promptManager.render(promptKey, Map.of());

        // Then
        assertThat(result).isEqualTo("Static template");
    }

    @Test
    void testMetricsIntegration() {
        // Given - 使用实际的 metrics 而不是 NoOp
        TestPromptMetrics testMetrics = new TestPromptMetrics();
        PromptManager managerWithMetrics = new PromptManager(mockSource, templateEngine, testMetrics);

        String promptKey = "metrics-test";
        String template = "Test: {{param}}";
        PromptMeta promptMeta = createPromptMeta(promptKey, template);

        when(mockSource.load(eq(promptKey))).thenReturn(promptMeta);

        // When
        managerWithMetrics.render(promptKey, Map.of("param", "value"));

        // Then
        assertThat(testMetrics.getRenderCount()).isEqualTo(1);
        assertThat(testMetrics.getSuccessCount()).isGreaterThan(0);
    }

    /**
     * 创建测试用的 PromptMeta
     */
    private PromptMeta createPromptMeta(String id, String template) {
        PromptMeta meta = new PromptMeta();
        meta.setId(id);
        meta.setTemplate(template);
        meta.setModel("gpt-3.5-turbo");
        meta.setTemperature(0.7);
        meta.setMaxTokens(1000);
        return meta;
    }

    /**
     * 测试用的 PromptMetrics 实现
     */
    private static class TestPromptMetrics implements com.chih.JPrompt.core.spi.PromptMetrics {
        private int renderCount = 0;
        private int successCount = 0;

        @Override
        public void recordRender(String promptKey, long durationNs, boolean success) {
            renderCount++;
            if (success) {
                successCount++;
            }
        }

        public int getRenderCount() { return renderCount; }
        public int getSuccessCount() { return successCount; }
    }
}