package com.chih.JPrompt.spring.scan;

import com.chih.JPrompt.spring.annotation.PromptScan;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class PromptMapperScannerRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        // 1. 获取 @PromptScan 注解属性
        AnnotationAttributes annoAttrs = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(PromptScan.class.getName()));

        if (annoAttrs == null) return;

        // 2. 创建扫描器
        ClassPathPromptMapperScanner scanner = new ClassPathPromptMapperScanner(registry);
        scanner.registerFilters();

        // 3. 确定扫描包路径
        List<String> basePackages = new ArrayList<>();
        
        for (String pkg : annoAttrs.getStringArray("value")) {
            if (StringUtils.hasText(pkg)) basePackages.add(pkg);
        }
        for (String pkg : annoAttrs.getStringArray("basePackages")) {
            if (StringUtils.hasText(pkg)) basePackages.add(pkg);
        }

        // 如果用户没填包名，默认扫描启动类所在的包
        if (basePackages.isEmpty()) {
            basePackages.add(importingClassMetadata.getClassName().substring(0, 
                             importingClassMetadata.getClassName().lastIndexOf('.')));
        }

        // 4. 执行扫描
        scanner.doScan(basePackages.toArray(new String[0]));
    }
}