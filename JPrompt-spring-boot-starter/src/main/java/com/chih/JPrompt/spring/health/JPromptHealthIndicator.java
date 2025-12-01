package com.chih.JPrompt.spring.health;

import com.chih.JPrompt.spring.SpringResourcePromptSource;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * JPrompt 健康检查指示器
 * 如果存在解析失败的 Prompt 文件，状态标记为 DOWN (或自定义状态)
 *
 * @author lizhiyuan
 * @since 2025/12/1 22:12
 */
public class JPromptHealthIndicator extends AbstractHealthIndicator {
    
    private final SpringResourcePromptSource promptSource;
    
    public JPromptHealthIndicator(SpringResourcePromptSource promptSource) {
        this.promptSource = promptSource;
    }
    
    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        Map<String, Throwable> errors = promptSource.getLoadErrors();
        
        if (errors.isEmpty()) {
            builder.up().withDetail("message", "All prompts loaded successfully.");
        } else {
            // 有文件加载失败
            builder.status(Status.DOWN).withDetail("message", "Some prompt files failed to load.")
                    .withDetail("errorCount", errors.size())
                    // 列出具体失败的文件名和错误信息
                    .withDetail("errors", errors.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getMessage())));
        }
    }
}