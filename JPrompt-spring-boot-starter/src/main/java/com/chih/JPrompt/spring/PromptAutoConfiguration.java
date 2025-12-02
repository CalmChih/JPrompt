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
 * Spring Boot 自动配置类
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
@Configuration
@EnableConfigurationProperties(JPromptProperties.class)
public class PromptAutoConfiguration {
    
    // 1. 定义 Watcher 专用线程池 (单线程)
    @Bean("jPromptWatcherExecutor")
    public ExecutorService jPromptWatcherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("jprompt-watcher-");
        executor.setDaemon(true);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }
    
    // 2. 定义防抖专用调度器 (单线程)
    @Bean("jPromptDebounceExecutor")
    public ScheduledExecutorService jPromptDebounceExecutor() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("jprompt-debouncer-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler.getScheduledExecutor();
    }
    
    @Bean
    @ConditionalOnMissingBean(PromptSource.class)
    public PromptSource promptSource(JPromptProperties properties,
            @Qualifier("jPromptWatcherExecutor") ExecutorService watcherExecutor,
            @Qualifier("jPromptDebounceExecutor") ScheduledExecutorService debounceExecutor) {
        return new SpringResourcePromptSource(properties.getLocations(), properties.getDebounceMillis(),
                watcherExecutor, debounceExecutor);
    }
    
    @Bean
    @ConditionalOnMissingBean(TemplateEngine.class)
    public TemplateEngine templateEngine() {
        return new MustacheTemplateEngine();
    }
    
    /**
     * 配置监控组件
     * 策略：
     * 1. 如果 classpath 下有 Micrometer 且容器里有 MeterRegistry -> 使用 Micrometer 实现
     * 2. 否则 -> 使用 NoOp 实现
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