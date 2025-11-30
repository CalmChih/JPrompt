package com.chih.JPrompt.core.impl;

import com.chih.JPrompt.core.spi.PromptMetrics;

public class NoOpPromptMetrics implements PromptMetrics {
    @Override
    public void recordRender(String promptKey, long durationNs, boolean success) {
        // Do nothing
    }
}