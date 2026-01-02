# JPrompt API 参考文档

## 目录

- [核心注解](#核心注解)
- [SPI 接口](#spi-接口)
- [核心引擎类](#核心引擎类)
- [领域模型](#领域模型)
- [异常体系](#异常体系)
- [Spring Boot 集成](#spring-boot-集成)

---

## 核心注解

### @PromptMapper

标记一个接口为 Prompt 映射器接口,被标记的接口会被 Spring 容器扫描并自动生成实现类。

**包路径**: `com.chih.JPrompt.core.annotation.PromptMapper`

**说明**:
- 这是一个标记注解,无需指定属性
- Prompt 文件位置通过 `application.yml` 统一配置
- 所有 Mapper 接口共享全局配置的 Prompt 源

**示例**:
```java
@PromptMapper
public interface ChatPromptMapper {
    String greetUser(String name);
}

@PromptMapper
public interface AnalysisMapper {
    @Prompt("code_review")
    String reviewCode(String code);
}
```

**配置文件位置**:
```yaml
j-prompt:
  locations:
    - "classpath*:prompts/**/*.yaml"
    - "file:./config/prompts/*.yaml"
```

### @Prompt

标记在方法上,用于关联具体的 Prompt Key。

**包路径**: `com.chih.JPrompt.core.annotation.Prompt`

**属性**:
- `String value()` - 对应 YAML 文件中的 Key

**示例**:
```java
@PromptMapper
public interface MyMapper {
    // 方法名与 Key 一致时可省略
    String sayHello(String name);

    // 指定不同的 Key
    @Prompt("greeting_user")
    String greet(String name);
}
```

### @Param

指定方法参数对应的模板变量名。

**包路径**: `com.chih.JPrompt.core.annotation.Param`

**属性**:
- `String value()` - 模板变量名

**示例**:
```java
@PromptMapper
public interface MyMapper {
    // 参数名与变量名一致时可省略
    String analyze(String code);

    // 指定不同的变量名
    @Prompt("code_review")
    String review(@Param("sourceCode") String javaCode);
}
```

---

## SPI 接口

### PromptSource

Prompt 来源接口,支持扩展不同的存储源(如 File, Nacos, DB)。

**包路径**: `com.chih.JPrompt.core.spi.PromptSource`

**方法**:
- `Map<String, PromptMeta> loadAll()` - 加载所有的 Prompt 配置
- `void onChange(Consumer<PromptChangeEvent> listener)` - 注册变更监听回调
- `default PromptMeta load(String key)` - 按 ID 加载单个 Meta
- `default void close()` - 关闭资源

**示例**:
```java
public class DatabasePromptSource implements PromptSource {
    @Override
    public Map<String, PromptMeta> loadAll() {
        // 从数据库加载所有提示词
    }

    @Override
    public void onChange(Consumer<PromptChangeEvent> listener) {
        // 监听数据库变更事件
    }
}
```

### TemplateEngine

模板引擎 SPI 接口,允许用户替换底层的渲染逻辑。

**包路径**: `com.chih.JPrompt.core.spi.TemplateEngine`

**方法**:
- `CompiledPrompt compile(String template, String rootId, Function<String, String> partialLoader)` - 编译阶段:将字符串模板编译为可执行对象
- `String render(Object compiledTemplate, Map<String, Object> variables)` - 执行阶段:使用编译好的对象进行渲染

**示例**:
```java
public class FreemarkerTemplateEngine implements TemplateEngine {
    @Override
    public CompiledPrompt compile(String template, String rootId, Function<String, String> partialLoader) {
        // 使用 Freemarker 编译模板
    }

    @Override
    public String render(Object compiledTemplate, Map<String, Object> variables) {
        // 使用 Freemarker 渲染模板
    }
}
```

### PromptMetrics

监控指标 SPI 接口。

**包路径**: `com.chih.JPrompt.core.spi.PromptMetrics`

**方法**:
- `void recordRender(String promptKey, long durationNs, boolean success)` - 记录一次 Prompt 渲染

**示例**:
```java
public class CustomPromptMetrics implements PromptMetrics {
    @Override
    public void recordRender(String promptKey, long durationNs, boolean success) {
        // 自定义监控逻辑
    }
}
```

---

## 核心引擎类

### PromptManager

核心管理器,负责协调 Source 和 Cache,提供 Prompt 的加载、编译、渲染和热更新功能。

**包路径**: `com.chih.JPrompt.core.engine.PromptManager`

**构造函数**:
```java
// 全参构造
public PromptManager(PromptSource source, TemplateEngine templateEngine, PromptMetrics metrics)

// 简化构造(使用默认模板引擎)
public PromptManager(PromptSource source, TemplateEngine templateEngine)

// 最简构造(使用默认模板引擎和空指标)
public PromptManager(PromptSource source)
```

**核心方法**:
- `String render(String key, Map<String, Object> variables)` - 渲染指定 Prompt
- `PromptMeta getMeta(String key)` - 获取 Prompt 元数据

**性能特性**:
- 使用 Caffeine 高性能缓存
- 读写锁优化,支持高并发
- Copy-On-Write 热更新,零读阻塞
- 依赖追踪和级联更新

**示例**:
```java
// 创建 PromptManager
PromptSource source = new FilePromptSource("/prompts");
PromptManager manager = new PromptManager(source);

// 渲染 Prompt
Map<String, Object> vars = new HashMap<>();
vars.put("name", "Alice");
String result = manager.render("greeting", vars);

// 获取元数据
PromptMeta meta = manager.getMeta("greeting");
System.out.println("Model: " + meta.getModel());
```

### PromptMapperFactory

动态代理工厂,为接口生成代理实例。

**包路径**: `com.chih.JPrompt.core.engine.PromptMapperFactory`

**构造函数**:
```java
public PromptMapperFactory(PromptManager manager)
```

**核心方法**:
- `<T> T createMapper(Class<T> interfaceType)` - 创建 Mapper 接口的代理实例

**特性**:
- 反射元数据缓存,避免重复解析
- Fail-Fast 预验证,启动时检查接口定义
- 支持 String 和 PromptMeta 两种返回类型

**示例**:
```java
PromptManager manager = new PromptManager(source);
PromptMapperFactory factory = new PromptMapperFactory(manager);

// 创建代理
MyAiMapper mapper = factory.createMapper(MyAiMapper.class);

// 使用代理
String result = mapper.greetUser("Alice");
```

---

## 领域模型

### PromptMeta

提示词元数据配置类,对应 YAML 配置文件中的每个提示词配置项。

**包路径**: `com.chih.JPrompt.core.domain.PromptMeta`

**属性**:
- `String id` - 提示词的唯一标识 ID
- `String model` - 模型名称(如 gpt-4, gpt-3.5-turbo)
- `Double temperature` - 温度参数(0.0 - 2.0)
- `Integer maxTokens` - 最大生成 Token 数
- `Long timeout` - 超时时间(毫秒)
- `String template` - 提示词模板内容
- `String description` - 描述信息
- `Map<String, Object> extensions` - 扩展参数集合

**方法**:
- `void validate()` - 验证当前配置项是否合法
- `boolean isValid()` - 简单校验,确保有效
- `<T> T getExtension(String key)` - 获取扩展参数

**示例**:
```java
PromptMeta meta = new PromptMeta();
meta.setId("greeting");
meta.setModel("gpt-3.5-turbo");
meta.setTemplate("Hello {{name}}!");
meta.setTemperature(0.7);

// 添加扩展参数
meta.addExtension("top_p", 1.0);
meta.addExtension("frequency_penalty", 0.0);

// 验证
meta.validate();
```

### CompiledPrompt

编译模板封装类,包含编译后的模板引擎对象和依赖关系。

**包路径**: `com.chih.JPrompt.core.spi.CompiledPrompt`

**属性**:
- `Object engineObject` - 具体的模板引擎对象(如 Mustache)
- `Set<String> dependencies` - 编译过程中发现的子模板依赖 ID

**方法**:
- `Object getEngineObject()` - 获取引擎对象
- `Set<String> getDependencies()` - 获取依赖集合

### PromptChangeEvent

变更事件封装类,用于传递 Prompt 变更信息。

**包路径**: `com.chih.JPrompt.core.spi.PromptChangeEvent`

**方法**:
- `Set<String> getUpdated()` - 获取更新的 Key 集合
- `Set<String> getRemoved()` - 获取删除的 Key 集合
- `Map<String, PromptMeta> getUpdatedMetas()` - 获取更新的元数据映射

---

## 异常体系

### PromptNotFoundException

提示词未找到异常。

**包路径**: `com.chih.JPrompt.core.exception.PromptNotFoundException`

**触发场景**:
- 请求的 Prompt Key 不存在
- 缓存未命中且 Source 加载失败

### PromptRenderException

提示词渲染异常。

**包路径**: `com.chih.JPrompt.core.exception.PromptRenderException`

**触发场景**:
- 模板变量缺失
- 模板语法错误
- 渲染引擎执行失败

---

## Spring Boot 集成

### PromptAutoConfiguration

JPrompt Spring Boot 自动配置类。

**包路径**: `com.chih.JPrompt.spring.PromptAutoConfiguration`

**自动配置的 Bean**:
- `PromptSource` - 默认为 SpringResourcePromptSource
- `TemplateEngine` - 默认为 MustacheTemplateEngine
- `PromptMetrics` - 优先使用 MicrometerPromptMetrics,否则使用 NoOpPromptMetrics
- `PromptManager` - 核心管理器
- `PromptMapperFactory` - 代理工厂
- `JPromptHealthIndicator` - 健康检查器(当引入 Actuator 时)

**配置属性**:
- `j-prompt.locations` - 资源位置列表
- `j-prompt.debounce-millis` - 热更新防抖时间(默认 500ms)

**示例**:
```yaml
j-prompt:
  debounce-millis: 1000
  locations:
    - "classpath*:prompts/**/*.yaml"
    - "file:./config/prompts/*.yaml"
```

### SpringResourcePromptSource

基于 Spring Resource 的 Prompt 源实现。

**包路径**: `com.chih.JPrompt.spring.SpringResourcePromptSource`

**特性**:
- 支持 Spring Resource 抽象层
- 支持通配符和 ant-style 路径模式
- 支持 file:、classpath:、jar: 等协议
- Index-Only 模式,零 OOM 风险
- 热更新支持

**方法**:
- `Map<String, Throwable> getLoadErrors()` - 获取加载错误映射

### JPromptHealthIndicator

JPrompt 健康检查指示器。

**包路径**: `com.chih.JPrompt.spring.health.JPromptHealthIndicator`

**健康状态**:
- **UP**: 所有 Prompt 文件加载成功
- **DOWN**: 存在解析失败的 Prompt 文件

**错误详情**:
```json
{
  "status": "DOWN",
  "details": {
    "message": "Some prompt files failed to load.",
    "errorCount": 2,
    "errors": {
      "bad.yaml": "Syntax Error...",
      "invalid.md": "Invalid FrontMatter..."
    }
  }
}
```

### JPromptProperties

配置属性绑定类。

**包路径**: `com.chih.JPrompt.spring.JPromptProperties`

**属性**:
- `List<String> locations` - 扫描路径列表
- `long debounceMillis` - 热更新防抖延迟(毫秒)

**默认值**:
- locations:
  - `classpath*:prompts/**/*.yaml`
  - `classpath*:prompts/**/*.yml`
  - `file:./prompts.yaml`
  - `file:./prompts.yml`
  - `file:./prompts/*.yaml`
  - `file:./prompts/*.yml`
  - `classpath*:prompts/**/*.md`
- debounceMillis: 500

---

## 类型支持

### 支持的返回类型

Mapper 接口方法支持以下返回类型:

1. **String** - 返回渲染后的文本
2. **PromptMeta** - 返回完整的元数据对象(包含 model、template 等)

**示例**:
```java
@PromptMapper
public interface MyMapper {
    // 返回渲染后的文本
    String greet(String name);

    // 返回完整元数据
    PromptMeta getGreetingMeta(String name);
}
```

### 支持的参数类型

Mapper 接口方法支持以下参数类型:

1. **基本类型** - String, Integer, Double, Boolean 等
2. **复杂对象** - 自定义 POJO(需提供 Getter 方法)
3. **集合类型** - List, Set, Map 等

**示例**:
```java
@PromptMapper
public interface MyMapper {
    // 基本类型
    String process(String text, Integer count, Boolean flag);

    // 复杂对象
    String analyze(OrderDTO order);

    // 集合类型
    String batchProcess(List<String> items);
}
```

---

## 更多信息

- [项目主页](../README.md)
- [Demo 文档](../JPrompt-demo/README.md)
- [开发者指南](developer-guide.md)
- [扩展开发指南](extension-guide.md)
- [配置参考手册](configuration.md)
