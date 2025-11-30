package com.chih.JPrompt.spring;

import com.chih.JPrompt.core.engine.PromptManager;
import com.chih.JPrompt.core.engine.PromptMapperFactory;
import com.chih.JPrompt.core.impl.MustacheTemplateEngine;
import com.chih.JPrompt.core.impl.NoOpPromptMetrics;
import com.chih.JPrompt.core.spi.PromptMetrics;
import com.chih.JPrompt.core.spi.PromptSource;
import com.chih.JPrompt.core.spi.TemplateEngine;
import com.chih.JPrompt.spring.metrics.MicrometerPromptMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 自动配置类
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
@Configuration
@EnableConfigurationProperties(PromptProperties.class)
public class PromptAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(PromptSource.class)
    public PromptSource promptSource(PromptProperties properties) {
        // 使用支持 Spring Resource 和热更新的 Source
        return new SpringResourcePromptSource(properties.getLocations());
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
}