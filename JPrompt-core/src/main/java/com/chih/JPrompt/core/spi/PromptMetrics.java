package com.chih.JPrompt.core.spi;

/**
 * 监控指标 SPI 接口
 *
 * @author lizhiyuan
 */
public interface PromptMetrics {

    /**
     * 记录一次 Prompt 渲染
     *
     * @param promptKey Prompt 的 ID
     * @param durationNs 耗时 (纳秒)
     * @param success 是否成功
     */
    void recordRender(String promptKey, long durationNs, boolean success);
}