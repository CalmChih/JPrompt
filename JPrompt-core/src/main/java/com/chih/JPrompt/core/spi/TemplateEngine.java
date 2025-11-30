package com.chih.JPrompt.core.spi;

import java.util.Map;

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
     * (在 PromptManager reload 时调用)
     *
     * @param template 原始模板字符串
     * @return 编译后的对象 (如 Mustache 对象)，由具体实现决定类型
     */
    Object compile(String template);
    
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