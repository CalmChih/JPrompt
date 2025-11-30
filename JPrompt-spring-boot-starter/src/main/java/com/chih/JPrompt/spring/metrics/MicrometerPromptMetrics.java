package com.chih.JPrompt.spring.metrics;

import com.chih.JPrompt.core.spi.PromptMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Micrometer 的监控实现
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