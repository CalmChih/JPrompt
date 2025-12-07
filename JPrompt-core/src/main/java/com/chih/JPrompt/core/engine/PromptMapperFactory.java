package com.chih.JPrompt.core.engine;

import com.chih.JPrompt.core.annotation.Param;
import com.chih.JPrompt.core.annotation.Prompt;
import com.chih.JPrompt.core.domain.PromptMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态代理工厂：为接口生成代理实例
 *
 * 优化说明：通过缓存方法元数据避免重复反射操作，显著提升性能。
 * 每个方法的反射信息（注解、参数名等）只会在第一次调用时解析一次，
 * 后续调用直接使用缓存结果。
 *
 * Fail-Fast：在创建代理时预验证所有方法，避免运行时才发现错误。
 *
 * @author lizhiyuan
 * @since 2025/11/30
 */
public class PromptMapperFactory {

    private static final Logger log = LoggerFactory.getLogger(PromptMapperFactory.class);

    private final PromptManager manager;

    // 方法元数据缓存：避免每次调用时都进行昂贵的反射操作
    private final Map<Method, MethodMetadata> methodCache = new ConcurrentHashMap<>();

    public PromptMapperFactory(PromptManager manager) {
        this.manager = manager;
    }

    /**
     * 方法元数据缓存类
     *
     * 缓存一个方法的所有反射信息，避免在每次调用时重复获取。
     * 这些信息在运行时不会改变，所以缓存是安全的。
     */
    private static class MethodMetadata {
        // 方法对应的 Prompt Key
        final String promptKey;

        // 参数信息数组：每个参数包含参数名和是否需要特殊处理
        final ParameterInfo[] parameterInfos;

        // 返回类型
        final Class<?> returnType;

        MethodMetadata(String promptKey, ParameterInfo[] parameterInfos, Class<?> returnType) {
            this.promptKey = promptKey;
            this.parameterInfos = parameterInfos;
            this.returnType = returnType;
        }
    }

    /**
     * 参数信息缓存类
     */
    private static class ParameterInfo {
        // 参数名（用于模板变量名）
        final String parameterName;

        // 参数在方法参数列表中的位置
        final int index;

        ParameterInfo(String parameterName, int index) {
            this.parameterName = parameterName;
            this.index = index;
        }
    }

    /**
     * 解析方法的元数据信息并缓存（带 Fail-Fast 验证）
     *
     * 这个方法只会在每个方法第一次被调用时执行一次，
     * 所有反射信息都会被缓存到 MethodMetadata 对象中。
     *
     * Fail-Fast：在启动时验证返回类型，避免运行时才发现错误。
     *
     * @param method 需要解析的方法
     * @return 方法元数据对象
     * @throws IllegalArgumentException 如果方法返回类型不被支持
     */
    private MethodMetadata parseMethodMetadata(Method method) {
        // 1. 解析 Prompt Key
        String promptKey;
        Prompt promptAnno = method.getAnnotation(Prompt.class);
        if (promptAnno != null) {
            promptKey = promptAnno.value();
        } else {
            // 默认策略：使用方法名
            promptKey = method.getName();
        }

        // 2. 解析参数信息
        Parameter[] parameters = method.getParameters();
        ParameterInfo[] parameterInfos = new ParameterInfo[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            String varName;

            Param paramAnno = param.getAnnotation(Param.class);
            if (paramAnno != null) {
                varName = paramAnno.value();
            } else {
                // 默认策略：使用参数名
                // 注意：需要编译时开启 -parameters 选项，否则得到的是 arg0, arg1
                varName = param.getName();
            }

            parameterInfos[i] = new ParameterInfo(varName, i);
        }

        // 3. 获取并验证返回类型
        Class<?> returnType = method.getReturnType();

        // Fail-Fast 验证：检查返回类型是否支持
        validateReturnType(method, returnType);

        return new MethodMetadata(promptKey, parameterInfos, returnType);
    }

    /**
     * 验证方法返回类型是否被支持
     *
     * @param method 被验证的方法
     * @param returnType 返回类型
     * @throws IllegalArgumentException 如果返回类型不被支持
     */
    private void validateReturnType(Method method, Class<?> returnType) {
        if (returnType.equals(String.class) || returnType.equals(PromptMeta.class)) {
            return; // 支持的类型
        }

        // 构建详细的错误信息，帮助开发者快速定位问题
        String errorMsg = String.format(
            "Unsupported return type '%s' in method '%s' of interface '%s'. " +
            "Supported return types are: String and PromptMeta. " +
            "Please update the method signature to use a supported return type.",
            returnType.getSimpleName(),
            method.getName(),
            method.getDeclaringClass().getName()
        );

        throw new IllegalArgumentException(errorMsg);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T createMapper(Class<T> interfaceType) {
        // Fail-Fast 预验证：在创建代理时就验证所有方法，而不是等到调用时才发现错误
        validateInterfaceMethods(interfaceType);

        return (T) Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class[]{interfaceType},
                new PromptInvocationHandler()
        );
    }

    /**
     * 预验证接口中的所有方法是否符合要求
     *
     * Fail-Fast 策略：在启动时就检查所有方法，确保没有不支持的返回类型，
     * 避免在运行时才发现错误。
     *
     * @param interfaceType 要验证的接口类型
     * @throws IllegalArgumentException 如果接口中有不支持的方法
     */
    private <T> void validateInterfaceMethods(Class<T> interfaceType) {
        Method[] methods = interfaceType.getMethods();

        for (Method method : methods) {
            // 跳过 Object 的方法
            if (Object.class.equals(method.getDeclaringClass())) {
                continue;
            }

            // 验证每个方法的返回类型
            Class<?> returnType = method.getReturnType();
            if (!returnType.equals(String.class) && !returnType.equals(PromptMeta.class)) {
                String errorMsg = String.format(
                    "Interface '%s' method '%s' has unsupported return type '%s'. " +
                    "Supported return types are: String and PromptMeta. " +
                    "Please update the method signature before using this interface.",
                    interfaceType.getName(),
                    method.getName(),
                    returnType.getSimpleName()
                );

                throw new IllegalArgumentException(errorMsg);
            }
        }

        log.debug("Interface validation passed for '{}' ({} methods validated)",
                 interfaceType.getSimpleName(), methods.length);
    }
    
      class PromptInvocationHandler implements InvocationHandler {
        private final PromptManager manager;

        public PromptInvocationHandler() {
            this.manager = PromptMapperFactory.this.manager;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, args);
            }

            // 关键优化：使用缓存的方法元数据，避免重复反射操作
            MethodMetadata metadata = methodCache.computeIfAbsent(method, PromptMapperFactory.this::parseMethodMetadata);

            // 1. 快速提取参数（使用缓存的参数信息）
            Map<String, Object> vars = extractArguments(metadata.parameterInfos, args);

            // 2. 根据缓存的返回类型处理
            return processResult(metadata.returnType, metadata.promptKey, vars);
        }

        /**
         * 根据缓存的参数信息快速提取方法参数
         *
         * 使用预解析的 ParameterInfo 数组，避免每次调用时重复反射获取参数名和注解。
         */
        private Map<String, Object> extractArguments(ParameterInfo[] parameterInfos, Object[] args) {
            if (args == null || parameterInfos.length == 0) {
                return Collections.emptyMap();
            }

            Map<String, Object> vars = new HashMap<>(parameterInfos.length);
            for (ParameterInfo paramInfo : parameterInfos) {
                // 直接使用预解析的参数名，避免反射调用
                vars.put(paramInfo.parameterName, args[paramInfo.index]);
            }
            return vars;
        }

        /**
         * 根据缓存的返回类型信息处理结果
         *
         * 使用预解析的返回类型信息，避免每次调用时重复获取。
         */
        private Object processResult(Class<?> returnType, String promptKey, Map<String, Object> vars) {
            // Case A: 返回 String (只返回渲染后的文本)
            if (returnType.equals(String.class)) {
                return manager.render(promptKey, vars);
            }

            // Case B: 返回 PromptMeta (返回完整对象，包含 model 等参数)
            if (returnType.equals(PromptMeta.class)) {
                PromptMeta original = manager.getMeta(promptKey);
                if (original == null) {
                    return null;
                }

                // 拷贝一份对象，避免修改缓存
                PromptMeta result = new PromptMeta();
                result.setId(original.getId());
                result.setModel(original.getModel());
                result.setTemperature(original.getTemperature());
                result.setMaxTokens(original.getMaxTokens());
                result.setTimeout(original.getTimeout());
                // 关键：将模板渲染为最终文本
                result.setTemplate(manager.render(promptKey, vars));
                if (original.getExtensions() != null) {
                    original.getExtensions().forEach(result::addExtension);
                }
                return result;
            }

            throw new UnsupportedOperationException("Unsupported return type: " + returnType.getName());
        }
    }
}