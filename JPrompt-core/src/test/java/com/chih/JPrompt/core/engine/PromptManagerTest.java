package com.chih.JPrompt.core.engine;

import com.chih.JPrompt.core.domain.PromptMeta;
import com.chih.JPrompt.core.exception.PromptNotFoundException;
import com.chih.JPrompt.core.spi.PromptMetrics;
import com.chih.JPrompt.core.spi.PromptSource;
import com.chih.JPrompt.core.spi.TemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptManagerTest {

    @Mock
    private PromptSource promptSource;
    @Mock private TemplateEngine templateEngine;
    @Mock private PromptMetrics promptMetrics;

    private PromptManager promptManager;

    @BeforeEach
    void setUp() {
        // 模拟 source 加载数据
        PromptMeta meta = new PromptMeta();
        meta.setTemplate("Hello {{name}}");
        when(promptSource.loadAll()).thenReturn(Map.of("test_key", meta));
        
        // 初始化 Manager (会触发一次 loadAll)
        promptManager = new PromptManager(promptSource, templateEngine, promptMetrics);
    }

    @Test
    void testGetAndRenderSuccess() {
        when(templateEngine.render(anyString(), anyMap())).thenReturn("Hello Java");

        String result = promptManager.render("test_key", Map.of("name", "Java"));

        assertThat(result).isEqualTo("Hello Java");
        // 验证 Metrics 被调用
        verify(promptMetrics).recordRender(eq("test_key"), anyLong(), eq(true));
    }

    @Test
    void testPromptNotFound() {
        assertThatThrownBy(() -> promptManager.render("unknown_key", Collections.emptyMap()))
                .isInstanceOf(PromptNotFoundException.class);
        
        // 验证 Metrics 记录了失败 (前提是你的 try-finally 块覆盖了异常抛出)
        verify(promptMetrics).recordRender(eq("unknown_key"), anyLong(), eq(false));
    }

    @Test
    void testHotReload() {
        // 1. 初始状态
        assertThat(promptManager.getMeta("test_key")).isNotNull();

        // 2. 模拟 Source 数据变更
        PromptMeta newMeta = new PromptMeta();
        newMeta.setTemplate("New Template");
        when(promptSource.loadAll()).thenReturn(Map.of("new_key", newMeta));

        // 3. 触发重载 (模拟回调)
        // 由于 reload 是私有的，我们需要通过构造函数里绑定的 onChange 回调来触发，
        // 或者简单点，我们假设 onChange 把回调暴露出来了。
        // 在这里，我们可以通过再次创建一个 Manager 来模拟，或者通过反射调用 reload。
        // 但更好的单元测试是：PromptManager 应该暴露一个 public void refresh() 方法供外部调用。
        
        // 假设我们在 PromptManager 加上了 public void refresh() { reload(); }
        // promptManager.refresh(); 
        
        // 如果不想改生产代码，我们可以用反射暴力调用 reload (仅限测试)
        try {
            var method = PromptManager.class.getDeclaredMethod("reload");
            method.setAccessible(true);
            method.invoke(promptManager);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 4. 验证数据已更新
        assertThat(promptManager.getMeta("test_key")).isNull(); // 旧的没了
        assertThat(promptManager.getMeta("new_key")).isNotNull(); // 新的有了
    }
}