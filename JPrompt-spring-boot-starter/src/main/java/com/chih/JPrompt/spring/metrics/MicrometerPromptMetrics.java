package com.chih.JPrompt.spring.metrics;

import com.chih.JPrompt.core.spi.PromptMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Micrometer 的监控实现
 * <p>
 * 监控指标说明：
 * <ul>
 *   <li>jprompt.render.timer: Prompt 渲染耗时，tags: prompt={promptKey}, result={success|failure}</li>
 *   <li>jprompt.render.count: Prompt 渲染次数计数器，tags: prompt={promptKey}, result={success|failure}</li>
 * </ul>
 * </p>
 * <p>
 * <strong>重要提示</strong>：如果您的应用会动态生成大量不同的 promptKey，
 * 建议考虑以下优化策略：
 * <ul>
 *   <li>使用有意义的 promptKey 而不是随机字符串</li>
 *   <li>在高频场景下考虑禁用 per-prompt 监控以避免基数爆炸</li>
 *   <li>定期监控 Prometheus 等监控系统的内存使用情况</li>
 * </ul>
 * </p>
 */
public class MicrometerPromptMetrics implements PromptMetrics {

    private final MeterRegistry registry;

    public MicrometerPromptMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordRender(String promptKey, long durationNs, boolean success) {
        // 1. 记录耗时 (Timer)
        // metric name: jprompt.render.timer
        // tags: key=prompt_id, result=success/failure
        Timer.builder("jprompt.render.timer")
                .description("Timer for prompt rendering")
                .tag("prompt", promptKey)
                .tag("result", success ? "success" : "failure")
                .register(registry)
                .record(durationNs, TimeUnit.NANOSECONDS);

        // 2. 记录调用次数 (Counter)
        // metric name: jprompt.render.count
        Counter.builder("jprompt.render.count")
                .description("Counter for prompt rendering")
                .tag("prompt", promptKey)
                .tag("result", success ? "success" : "failure")
                .register(registry)
                .increment();
    }
}