package com.chih.JPrompt.core.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个接口为 Prompt 映射器接口
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface PromptMapper {
    /**
     * 指定加载的文件名，默认为 prompts.yaml
     */
    String file() default "prompts.yaml";
}