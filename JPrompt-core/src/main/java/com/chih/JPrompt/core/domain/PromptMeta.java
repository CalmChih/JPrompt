package com.chih.JPrompt.core.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 提示词元数据配置类
 * <p>
 * 该类对应 YAML 配置文件中的每一个具体的提示词配置项。
 * 承载了模型参数、模板内容以及业务控制参数。
 * </p>
 *
 * @author lizhiyuan
 * @since 2025/11/30 20:41
 */
public class PromptMeta implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    /**
     * 提示词的唯一标识 ID (通常对应 YAML 的 Key)
     */
    private String id;
    
    /**
     * 模型名称 (例如: gpt-4, gpt-3.5-turbo, claude-2)
     */
    private String model;
    
    /**
     * 温度参数 (0.0 - 1.0)
     * 控制生成的随机性：值越大越发散，值越小越精确
     */
    private Double temperature;
    
    /**
     * 最大生成 Token 数
     * 用于防止模型生成过长的内容消耗配额
     */
    private Integer maxTokens;
    
    /**
     * 超时时间 (毫秒)
     * 建议设置默认值，防止接口长时间阻塞
     */
    private Long timeout;
    
    /**
     * 提示词模板内容
     * 支持 {{variable}} 变量占位符
     */
    private String template;
    
    /**
     * 描述信息
     * 用于在管理界面展示该提示词的用途，或帮助开发者理解
     */
    private String description;
    
    /**
     * 扩展参数集合
     * 用于存储 YAML 中定义但 PromptMeta 未显式声明的字段 (如 top_p, presence_penalty)
     */
    private final Map<String, Object> extensions = new HashMap<>();
    
    // =======================================================
    // 构造函数 (Constructors)
    // =======================================================
    
    /**
     * 无参构造函数 (Jackson 反序列化必须)
     */
    public PromptMeta() {
    }
    
    /**
     * 全参构造函数
     */
    public PromptMeta(String id, String model, Double temperature, Integer maxTokens, Long timeout, String template, String description) {
        this.id = id;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeout = timeout;
        this.template = template;
        this.description = description;
    }
    
    // =======================================================
    // 动态扩展支持 (Jackson Magic)
    // =======================================================
    
    /**
     * 捕获所有未映射的 YAML 属性
     */
    @JsonAnySetter
    public void addExtension(String key, Object value) {
        this.extensions.put(key, value);
    }
    
    /**
     * 序列化时将扩展属性平铺输出
     */
    @JsonAnyGetter
    public Map<String, Object> getExtensions() {
        return extensions;
    }
    
    /**
     * 获取扩展参数 (类型安全辅助方法)
     */
    @SuppressWarnings("unchecked")
    public <T> T getExtension(String key) {
        return (T) extensions.get(key);
    }
    
    /**
     * 验证当前配置项是否合法
     */
    public void validate() {
        if (template == null || template.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt template cannot be empty" + (id != null ? " for id: " + id : ""));
        }
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalArgumentException("Temperature must be between 0.0 and 2.0 for prompt: " + id);
        }
        if (maxTokens != null && maxTokens <= 0) {
            throw new IllegalArgumentException("MaxTokens must be positive for prompt: " + id);
        }
    }
    
    // =======================================================
    // Getter & Setter 方法
    // =======================================================
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public Double getTemperature() {
        return temperature;
    }
    
    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
    
    public Integer getMaxTokens() {
        return maxTokens;
    }
    
    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
    
    public Long getTimeout() {
        return timeout;
    }
    
    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }
    
    public String getTemplate() {
        return template;
    }
    
    public void setTemplate(String template) {
        this.template = template;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    // =======================================================
    // 辅助方法 (toString)
    // =======================================================
    
    @Override
    public String toString() {
        return "PromptMeta{" +
                "id='" + id + '\'' +
                ", model='" + model + '\'' +
                ", temperature=" + temperature +
                ", maxTokens=" + maxTokens +
                ", timeout=" + timeout +
                ", description='" + description + '\'' +
                ", templateLength=" + (template != null ? template.length() : 0) +
                '}';
    }
    
    /**
     * 简单的校验逻辑，确保加载的配置是有效的
     * @return 如果有效返回 true
     */
    public boolean isValid() {
        return template != null && !template.trim().isEmpty();
    }
    
    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof PromptMeta that)) {
            return false;
        }
        
        return id.equals(that.id) && Objects.equals(model, that.model) && Objects.equals(temperature, that.temperature)
                && Objects.equals(maxTokens, that.maxTokens) && Objects.equals(timeout, that.timeout)
                && template.equals(that.template) && Objects.equals(description, that.description) && Objects.equals(
                extensions, that.extensions);
    }
    
    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + Objects.hashCode(model);
        result = 31 * result + Objects.hashCode(temperature);
        result = 31 * result + Objects.hashCode(maxTokens);
        result = 31 * result + Objects.hashCode(timeout);
        result = 31 * result + template.hashCode();
        result = 31 * result + Objects.hashCode(description);
        result = 31 * result + Objects.hashCode(extensions);
        return result;
    }
}