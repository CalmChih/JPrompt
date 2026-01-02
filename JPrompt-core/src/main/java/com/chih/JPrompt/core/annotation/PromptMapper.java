package com.chih.JPrompt.core.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个接口为 Prompt 映射器接口。
 * <p>
 * 这是 JPrompt 框架的核心注解，用于标记需要生成 Prompt 访问代理的接口。
 * 被标记的接口会被 Spring 容器扫描，并自动生成基于动态代理的实现类。
 * </p>
 *
 * <h3>功能特性：</h3>
 * <ul>
 *   <li><strong>自动代理生成</strong>：运行时动态创建接口实现</li>
 *   <li><strong>Spring 集成</strong>：支持依赖注入和 AOP</li>
 *   <li><strong>类型安全</strong>：编译时检查方法签名和参数</li>
 *   <li><strong>统一配置</strong>：所有 Mapper 共享全局配置的 Prompt 源</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 1. 定义映射器接口
 * @PromptMapper
 * public interface ChatPromptMapper {
 *
 *     @Prompt("greeting")
 *     String greetUser(@Param("name") String name);
 *
 *     @Prompt("farewell")
 *     String sayGoodbye(@Param("name") String name, @Param("style") String style);
 * }
 *
 * // 2. 注入并使用
 * @Service
 * public class ChatService {
 *
 *     @Autowired
 *     private ChatPromptMapper promptMapper;
 *
 *     public String processGreeting(String userName) {
 *         return promptMapper.greetUser(userName); // 自动调用 Prompt 引擎
 *     }
 * }
 * }</pre>
 *
 * <h3>文件配置：</h3>
 * <p>
 * Prompt 文件位置通过 {@code application.yml} 统一配置：
 * </p>
 * <pre>
 * j-prompt:
 *   locations:
 *     - "classpath*:prompts&#47;**&#47;*.yaml"
 *     - "file:./config/prompts/*.yaml"
 * }</pre>
 *
 * @author lizhiyuan
 * @since 2025/11/30
 * @see Prompt
 * @see Param
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface PromptMapper {
    // 无属性，仅作为标记注解使用
    // Prompt 文件位置通过 application.yml 统一配置
}