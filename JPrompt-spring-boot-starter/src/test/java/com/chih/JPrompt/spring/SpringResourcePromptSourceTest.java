package com.chih.JPrompt.spring;

import com.chih.JPrompt.core.domain.PromptMeta;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringResourcePromptSourceTest {

    // 只需要测试解析逻辑，不需要启动 Watcher
    private final SpringResourcePromptSource source = new SpringResourcePromptSource(List.of(), 500);

    @Test
    void testParseMarkdownWithFrontMatter() throws Exception {
        // 1. 准备测试数据
        String mdContent = 
            "---\n" +
            "model: gpt-4\n" +
            "temperature: 0.5\n" +
            "---\n" +
            "# Task\n" +
            "Analyze this code.";
            
        // 模拟一个文件资源
        Resource resource = new ByteArrayResource(mdContent.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "test_prompt.md";
            }
        };

        // 2. 调用私有方法 parseMarkdown (或者提取为 protected/package-private 以便测试)
        // 这里使用反射来测试私有方法
        Method method = SpringResourcePromptSource.class.getDeclaredMethod("parseMarkdown", Resource.class);
        method.setAccessible(true);
        
        @SuppressWarnings("unchecked") Map<String, PromptMeta> result = (Map<String, PromptMeta>) method.invoke(source, resource);

        // 3. 断言
        assertThat(result).hasSize(1);
        PromptMeta meta = result.get("test_prompt");
        
        assertThat(meta).isNotNull();
        assertThat(meta.getModel()).isEqualTo("gpt-4"); // 验证 YAML 解析
        assertThat(meta.getTemperature()).isEqualTo(0.5);
        assertThat(meta.getTemplate()).contains("# Task").contains("Analyze this code."); // 验证 Body 解析
    }

    @Test
    void testParseMarkdownWithoutFrontMatter() throws Exception {
        String mdContent = "Just a simple prompt.";
        
        Resource resource = new ByteArrayResource(mdContent.getBytes()) {
            @Override
            public String getFilename() {
                return "simple.md";
            }
        };

        Method method = SpringResourcePromptSource.class.getDeclaredMethod("parseMarkdown", Resource.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, PromptMeta> result = (Map<String, PromptMeta>) method.invoke(source, resource);

        PromptMeta meta = result.get("simple");
        assertThat(meta.getModel()).isNull(); // 无元数据
        assertThat(meta.getTemplate()).isEqualTo("Just a simple prompt.");
    }
}