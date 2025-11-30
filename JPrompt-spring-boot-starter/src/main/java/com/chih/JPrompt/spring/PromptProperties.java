package com.chih.JPrompt.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * 提示词路径配置
 * @author lizhiyuan
 * @since 2025/11/30 21:21
*/
@ConfigurationProperties(prefix = "prompt")
public class PromptProperties {
    
    /**
     * 扫描路径列表
     * 支持 classpath: (只读) 和 file: (支持热更新)
     */
    private List<String> locations = new ArrayList<>();
    
    public PromptProperties() {
        // 1. 默认约定：扫描 classpath 下 prompts 目录的所有 yaml
        locations.add("classpath*:prompts/**/*.yaml");
        locations.add("classpath*:prompts/**/*.yml");
        
        // 2. 默认约定：扫描项目根目录下的 prompts.yaml (方便本地调试热更)
        locations.add("file:./prompts.yaml");
        locations.add("file:./prompts.yml");
        locations.add("file:./prompts/*.yaml");
        locations.add("file:./prompts/*.yml");
        
        locations.add("classpath*:prompts/**/*.md");
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }
}