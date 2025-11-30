package com.chih.JPrompt.spring.scan;

import com.chih.JPrompt.core.annotation.PromptMapper;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Set;

public class ClassPathPromptMapperScanner extends ClassPathBeanDefinitionScanner {

    public ClassPathPromptMapperScanner(BeanDefinitionRegistry registry) {
        super(registry, false); // false = 不使用默认过滤器
    }

    public void registerFilters() {
        // 只扫描带有 @PromptMapper 注解的接口
        addIncludeFilter(new AnnotationTypeFilter(PromptMapper.class));
    }

    /**
     * 重写此方法以支持接口扫描 (默认 Spring 只扫描具体类)
     */
    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        return beanDefinition.getMetadata().isInterface() && 
               beanDefinition.getMetadata().isIndependent();
    }

    /**
     * 偷天换日：将扫描到的 Bean 定义修改为 PromptFactoryBean
     */
    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);

        if (beanDefinitions.isEmpty()) {
            logger.warn("No PromptMapper was found in '" + java.util.Arrays.toString(basePackages) + 
                        "'. Please check your configuration.");
        }

        for (BeanDefinitionHolder holder : beanDefinitions) {
            GenericBeanDefinition definition = (GenericBeanDefinition) holder.getBeanDefinition();
            String beanClassName = definition.getBeanClassName();
            
            // 1. 设置构造函数参数 (传入接口的 Class)
            definition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName);
            
            // 2. 将 Bean 的真实类修改为 FactoryBean
            definition.setBeanClass(PromptFactoryBean.class);
            
            // 3. 开启按类型自动注入 (以便注入 PromptMapperFactory)
            definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        }

        return beanDefinitions;
    }
}