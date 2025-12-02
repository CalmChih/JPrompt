package com.chih.JPrompt.core.spi;

import com.chih.JPrompt.core.domain.PromptMeta;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 提示词变更事件
 *
 * @author lizhiyuan
 * @since 2025/12/2 21:15
 */
public class PromptChangeEvent {
    
    // 发生变更（新增或修改）的 Prompt
    private final Map<String, PromptMeta> updated;
    
    // 被删除的 Prompt ID
    private final Set<String> removed;
    
    public PromptChangeEvent(Map<String, PromptMeta> updated, Set<String> removed) {
        this.updated = (updated != null) ? updated : Collections.emptyMap();
        this.removed = (removed != null) ? removed : Collections.emptySet();
    }
    
    public Map<String, PromptMeta> getUpdated() {
        return updated;
    }
    
    public Set<String> getRemoved() {
        return removed;
    }
}