package com.chih.JPrompt.spring.annotation;

import com.chih.JPrompt.spring.scan.PromptMapperScannerRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用 PromptMapper 自动扫描
 * <p>
 * 用法: @PromptScan("com.example.mapper")
 * </p>
 * @author lizhiyuan
 * @since 2025/11/30 21:41
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(PromptMapperScannerRegistrar.class) // 引入注册逻辑
public @interface PromptScan {

    /**
     * 扫描包路径，别名 value
     */
    String[] value() default {};

    /**
     * 扫描包路径
     */
    String[] basePackages() default {};
}