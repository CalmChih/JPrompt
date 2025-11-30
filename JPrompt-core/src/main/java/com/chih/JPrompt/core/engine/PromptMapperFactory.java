package com.chih.JPrompt.core.engine;

import com.chih.JPrompt.core.annotation.Param;
import com.chih.JPrompt.core.annotation.Prompt;
import com.chih.JPrompt.core.domain.PromptMeta;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * 动态代理工厂：为接口生成代理实例
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
public class PromptMapperFactory {
    
    private final PromptManager manager;
    
    public PromptMapperFactory(PromptManager manager) {
        this.manager = manager;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T createMapper(Class<T> interfaceType) {
        return (T) Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class[]{interfaceType},
                new PromptInvocationHandler(manager)
        );
    }
    
    static class PromptInvocationHandler implements InvocationHandler {
        private final PromptManager manager;
        
        public PromptInvocationHandler(PromptManager manager) {
            this.manager = manager;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, args);
            }
            
            Prompt promptAnno = method.getAnnotation(Prompt.class);
            if (promptAnno == null) {
                // 如果没有注解，可以扩展为执行默认逻辑，这里简单返回 null
                return null;
            }
            
            // 1. 提取参数
            Map<String, Object> vars = new HashMap<>();
            Parameter[] parameters = method.getParameters();
            if (parameters != null) {
                for (int i = 0; i < parameters.length; i++) {
                    Param paramAnno = parameters[i].getAnnotation(Param.class);
                    if (paramAnno != null && args[i] != null) {
                        vars.put(paramAnno.value(), args[i]);
                    }
                }
            }
            
            // 2. 根据返回类型处理
            Class<?> returnType = method.getReturnType();
            
            // Case A: 返回 String (只返回渲染后的文本)
            if (returnType.equals(String.class)) {
                return manager.render(promptAnno.value(), vars);
            }
            
            // Case B: 返回 PromptMeta (返回完整对象，包含 model 等参数)
            if (returnType.equals(PromptMeta.class)) {
                PromptMeta original = manager.getMeta(promptAnno.value());
                if (original == null) return null;
                
                // 拷贝一份对象，避免修改缓存
                PromptMeta result = new PromptMeta();
                result.setId(original.getId());
                result.setModel(original.getModel());
                result.setTemperature(original.getTemperature());
                result.setMaxTokens(original.getMaxTokens());
                result.setTimeout(original.getTimeout());
                // 关键：将模板渲染为最终文本
                result.setTemplate(manager.render(promptAnno.value(), vars));
                return result;
            }
            
            throw new UnsupportedOperationException("Unsupported return type: " + returnType.getName());
        }
    }
}