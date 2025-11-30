package com.chih.JPrompt.core.annotation;
import java.lang.annotation.*;

/**
 * 标记在方法上，用于关联具体的 Prompt Key
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Prompt {
    /**
     * 对应 YAML 文件中的 Key
     */
    String value();
}