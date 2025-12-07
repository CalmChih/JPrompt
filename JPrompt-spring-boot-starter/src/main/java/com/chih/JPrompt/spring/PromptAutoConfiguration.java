package com.chih.JPrompt.spring;

import com.chih.JPrompt.core.engine.PromptManager;
import com.chih.JPrompt.core.engine.PromptMapperFactory;
import com.chih.JPrompt.core.impl.MustacheTemplateEngine;
import com.chih.JPrompt.core.impl.NoOpPromptMetrics;
import com.chih.JPrompt.core.spi.PromptMetrics;
import com.chih.JPrompt.core.spi.PromptSource;
import com.chih.JPrompt.core.spi.TemplateEngine;
import com.chih.JPrompt.spring.health.JPromptHealthIndicator;
import com.chih.JPrompt.spring.metrics.MicrometerPromptMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * JPrompt Spring Boot 自动配置类。
 * <p>
 * 这是 JPrompt 与 Spring Boot 集成的核心配置类，负责自动创建和配置所有必要的 Bean。
 * 通过 Spring Boot 的条件注解机制，实现了智能的默认配置和用户自定义覆盖的支持。
 * </p>
 *
 * <h3>核心功能：</h3>
 * <ul>
 *   <li><strong>自动化配置</strong>：零配置启动，自动创建所需组件</li>
 *   <li><strong>条件装配</strong>：根据类路径和现有 Bean 智能选择实现</li>
 *   <li><strong>监控集成</strong>：自动集成 Micrometer 监控指标</li>
 *   <li><strong>健康检查</strong>：集成 Spring Boot Actuator 健康检查</li>
 *   <li><strong>线程池管理</strong>：提供专用的文件监听和防抖线程池</li>
 * </ul>
 *
 * <h3>Bean 配置策略：</h3>
 * <ul>
 *   <li><strong>@ConditionalOnMissingBean</strong>：允许用户自定义实现覆盖默认配置</li>
 *   <li><strong>@ConditionalOnClass</strong>：根据依赖存在性决定是否启用功能</li>
 *   <li><strong>内部配置类</strong>：模块化配置，按需启用</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 默认配置（application.yml）
 * jprompt:
 *   locations:
 *     - classpath:/prompts/
 *     - file:./custom-prompts/
 *   debounce-millis: 1000
 *
 * // 自定义组件（可选）
 * @Bean
 * @Primary
 * public PromptSource customPromptSource() {
 *     return new CustomPromptSource();
 * }
 * }</pre>
 *
 * @author lizhiyuan
 * @since 2025/11/30
 * @see JPromptProperties
 * @see SpringResourcePromptSource
 * @see PromptManager
 */
@Configuration
@EnableConfigurationProperties(JPromptProperties.class)
public class PromptAutoConfiguration {
    
    /**
     * 文件监听器专用线程池。
     * <p>
     * 创建专门用于文件系统监听的线程池。使用单线程配置，因为：
     * 1. 文件监听通常是 I/O 密集型操作，不需要多线程
     * 2. 避免并发访问文件系统造成竞争条件
     * 3. 降低资源消耗，提高性能可预测性
     * </p>
     *
     * @return 配置好的 ExecutorService，Bean 名称为 "jPromptWatcherExecutor"
     */
    @Bean("jPromptWatcherExecutor")
    public ExecutorService jPromptWatcherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("jprompt-watcher-");
        executor.setDaemon(true); /* 设置为守护线程，不阻止 JVM 退出 */
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    /**
     * 防抖定时器专用线程池。
     * <p>
     * 创建专门用于防抖延迟处理的调度线程池。使用单线程配置，因为：
     * 1. 防抖逻辑是时序敏感的，单线程可以避免并发问题
     * 2. 防抖操作通常是轻量级的，单线程足够
     * 3. 确保防抖事件的顺序性处理
     * </p>
     *
     * @return 配置好的 ScheduledExecutorService，Bean 名称为 "jPromptDebounceExecutor"
     */
    @Bean("jPromptDebounceExecutor")
    public ScheduledExecutorService jPromptDebounceExecutor() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("jprompt-debouncer-");
        scheduler.setDaemon(true); /* 设置为守护线程，不阻止 JVM 退出 */
        scheduler.initialize();
        return scheduler.getScheduledExecutor();
    }
    
    /**
     * 默认 Prompt 源配置。
     * <p>
     * 当用户没有自定义 PromptSource Bean 时，自动创建 SpringResourcePromptSource。
     * 使用配置文件中的位置信息和防抖设置，注入专用的线程池。
     * </p>
     *
     * @param properties JPrompt 配置属性，包含资源位置和防抖设置
     * @param watcherExecutor 文件监听线程池，由 jPromptWatcherExecutor Bean 提供
     * @param debounceExecutor 防抖调度线程池，由 jPromptDebounceExecutor Bean 提供
     * @return 配置好的 SpringResourcePromptSource 实例
     */
    @Bean
    @ConditionalOnMissingBean(PromptSource.class)
    public PromptSource promptSource(JPromptProperties properties,
            @Qualifier("jPromptWatcherExecutor") ExecutorService watcherExecutor,
            @Qualifier("jPromptDebounceExecutor") ScheduledExecutorService debounceExecutor) {
        return new SpringResourcePromptSource(properties.getLocations(), properties.getDebounceMillis(),
                watcherExecutor, debounceExecutor);
    }

    /**
     * 默认模板引擎配置。
     * <p>
     * 当用户没有自定义 TemplateEngine Bean 时，自动创建 MustacheTemplateEngine。
     * Mustache 是一种简单、逻辑less的模板系统，非常适合 Prompt 模板场景。
     * </p>
     *
     * @return 配置好的 MustacheTemplateEngine 实例
     */
    @Bean
    @ConditionalOnMissingBean(TemplateEngine.class)
    public TemplateEngine templateEngine() {
        return new MustacheTemplateEngine();
    }

    /**
     * 监控组件配置。
     * <p>
     * 采用智能策略选择监控实现：
     * 1. 优先选择：如果 Micrometer 在类路径中且存在 MeterRegistry Bean，使用 Micrometer 实现
     * 2. 保底选择：否则使用 NoOp 实现，不产生任何监控指标
     * </p>
     * <p>
     * 这种策略确保：
     * - 在生产监控环境中自动启用丰富的指标
     * - 在简单应用中不引入额外的依赖和开销
     * </p>
     */
    @Configuration
    @ConditionalOnClass(MeterRegistry.class)
    static class MetricsConfiguration {
        
        @Bean
        @ConditionalOnMissingBean(PromptMetrics.class)
        public PromptMetrics promptMetrics(MeterRegistry registry) {
            return new MicrometerPromptMetrics(registry);
        }
    }
    
    // 保底配置：如果没有 Metrics 环境，注入空实现
    @Bean
    @ConditionalOnMissingBean(PromptMetrics.class)
    public PromptMetrics defaultPromptMetrics() {
        return new NoOpPromptMetrics();
    }
    
    @Bean
    @ConditionalOnMissingBean(PromptManager.class)
    public PromptManager promptManager(PromptSource source,
            TemplateEngine engine,
            PromptMetrics metrics) {
        return new PromptManager(source, engine, metrics);
    }
    
    @Bean
    @ConditionalOnMissingBean(PromptMapperFactory.class)
    public PromptMapperFactory promptMapperFactory(PromptManager manager) {
        return new PromptMapperFactory(manager);
    }
    
    /**
     * 健康检查自动配置
     * 只有当引入了 Actuator (存在 HealthIndicator 类) 时才生效
     */
    @Configuration
    @ConditionalOnClass(HealthIndicator.class)
    static class HealthCheckConfiguration {
        
        @Bean
        @ConditionalOnMissingBean(name = "jPromptHealthIndicator")
        public JPromptHealthIndicator jPromptHealthIndicator(PromptSource source) { // 1. 改为注入接口
            
            // 2. 检查：只有当实现类是我们提供的 SpringResourcePromptSource 时，才启用健康检查
            if (source instanceof SpringResourcePromptSource) {
                return new JPromptHealthIndicator((SpringResourcePromptSource) source);
            }
            
            // 3. 如果用户自定义了 PromptSource（不支持 getLoadErrors），则不注册此 Bean (返回 null 是合法的)
            return null;
        }
    }
}