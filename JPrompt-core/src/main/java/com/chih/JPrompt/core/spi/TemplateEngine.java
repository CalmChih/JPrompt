package com.chih.JPrompt.core.spi;

import java.util.Map;
import java.util.function.Function;

/**
 * 模板引擎 SPI 接口
 * 允许用户替换底层的渲染逻辑
 * 支持预编译模式，提升运行时性能
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
public interface TemplateEngine {
    
    /**
     * 1. 编译阶段：将字符串模板编译为可执行对象
     *
     * @param template 原始模板字符串
     * @param partialLoader 子模板加载器 (输入子模板名称，返回子模板内容)。如果为 null，则不支持子模板。
     * @return 编译后的对象
     */
    Object compile(String template, Function<String, String> partialLoader);
    
    /**
     * 2. 执行阶段：使用编译好的对象进行渲染
     * (在 render 时调用，无锁、无Map查找)
     *
     * @param compiledTemplate 编译后的对象 (来自于 compile 方法的返回值)
     * @param variables 变量上下文
     * @return 渲染后的字符串
     */
    String render(Object compiledTemplate, Map<String, Object> variables);
}