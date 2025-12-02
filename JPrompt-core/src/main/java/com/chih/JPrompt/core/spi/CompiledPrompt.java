package com.chih.JPrompt.core.spi;

import java.util.Set;

/**
 * 编译模板 获取模板依赖ID
 * @author lizhiyuan
 * @since 2025/12/2 21:16
*/
public class CompiledPrompt {
    
    // 具体的模板引擎对象 (如 Mustache)
    private final Object engineObject;
    
    // 编译过程中发现的子模板依赖 ID
    private final Set<String> dependencies;
    
    public CompiledPrompt(Object engineObject, Set<String> dependencies) {
        this.engineObject = engineObject;
        this.dependencies = dependencies;
    }
    
    public Object getEngineObject() {
        return engineObject;
    }
    
    public Set<String> getDependencies() {
        return dependencies;
    }
}