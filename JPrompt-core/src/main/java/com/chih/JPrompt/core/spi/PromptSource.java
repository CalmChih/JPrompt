package com.chih.JPrompt.core.spi;

import com.chih.JPrompt.core.domain.PromptMeta;
import java.util.Map;

/**
 * Prompt 来源接口 (SPI)
 * <p>
 * 遵循 SPI 设计模式，支持扩展不同的存储源（如 File, Nacos, DB）。
 * </p>
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
public interface PromptSource extends AutoCloseable{
    
    /**
     * 加载所有的 Prompt 配置
     * @return Map<String, PromptMeta> key为promptId
     */
    Map<String, PromptMeta> loadAll();
    
    /**
     * 注册变更监听回调
     * 当源数据发生变化时，实现类需要主动调用 callback.run()
     * @param callback 回调函数
     */
    void onChange(Runnable callback);
    
    /**
     * 关闭资源 (如线程池、WatchService 连接)
     * 默认空实现，方便简单实现类
     */
    @Override
    default void close() throws Exception {
        // Default no-op
    }
}