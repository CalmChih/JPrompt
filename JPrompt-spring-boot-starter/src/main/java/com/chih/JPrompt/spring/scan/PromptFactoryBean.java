package com.chih.JPrompt.spring.scan;

import com.chih.JPrompt.core.engine.PromptMapperFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Spring FactoryBean，用于创建 PromptMapper 的代理实例
 *
 * @param <T> 接口类型
 */
public class PromptFactoryBean<T> implements FactoryBean<T> {

    private Class<T> mapperInterface;

    // 自动注入核心工厂
    @Autowired
    private PromptMapperFactory promptMapperFactory;

    public PromptFactoryBean() {
    }

    public PromptFactoryBean(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }

    @Override
    public T getObject() throws Exception {
        // 委托给 Core 模块的 Factory 创建代理
        return promptMapperFactory.createMapper(mapperInterface);
    }

    @Override
    public Class<?> getObjectType() {
        return mapperInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public void setMapperInterface(Class<T> mapperInterface) {
        this.mapperInterface = mapperInterface;
    }
}