package com.chih.JPrompt.core.annotation;
import java.lang.annotation.*;

/**
 * 标记在方法参数上，用于指定模板变量的名称
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {
    /**
     * 对应模板 {{value}} 中的变量名
     */
    String value();
}