# JPrompt - Java Prompt Mapper

> The "MyBatis" for LLM Prompts.
> 像管理 SQL 一样管理你的 AI 提示词。

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%2F4.x-green.svg)]()
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()

**JPrompt** 是一个专为 Java/Spring 开发者设计的生产级 Prompt（提示词）管理框架。它旨在解决 Prompt 硬编码在 Java 字符串中难以维护、无法版本控制、无法热更新的痛点。

---

## ✨ 核心特性 (Key Features)

### 🚀 开发体验
- **接口化调用**：类似 MyBatis 的 Mapper 接口设计 (`@PromptMapper`)，自动生成代理实现，无需编写样板代码。
- **多格式支持**：
  - `.yaml`: 适合集中管理短文本提示词。
  - `.md` (Markdown): 支持 **FrontMatter** 元数据，适合编写包含代码块、Few-Shot 示例的复杂 Prompt。

### ⚡️ 高性能与低内存 (Performance & Memory)
- **极致内存优化 (Index-Only Pattern)**：
  - **Source 层**：采用“仅索引”策略，仅存储文件路径映射，**不缓存文件内容**，彻底杜绝 OOM 风险。
  - **Manager 层**：集成 **Caffeine** 高性能缓存，支持 LRU 淘汰和最大容量控制。
  - **Cache 瘦身**：编译后的模板对象自动丢弃原始字符串，减少 50%+ 堆内存占用。
- **预编译机制**：启动时/热更时预编译 Template，运行时 **零解析开销**。

### 🔄 智能热更新 (Intelligent Hot Reload)
- **精准增量更新 (Incremental Updates)**：基于 Push 模式的 Diff 计算，仅重编译发生变化的文件，拒绝全量重载。
- **级联依赖更新 (Cascading Re-compilation)**：
  - 内置 **编译期依赖追踪** 和 **倒排索引 (Inverted Index)**。
  - 当修改公共片段（如 `{{> common_header}}`）时，所有引用它的 Prompt 会自动检测并重编译。
- **智能防抖 (Debouncing)**：支持变更暂存与批量推送，完美处理编辑器“全部保存”时的高频文件事件。

### 🛡 生产级健壮性
- **可观测性 (Observability)**：
  - **Metrics**: 自动适配 Micrometer，暴露渲染耗时 (`timer`) 和调用次数 (`counter`)。
  - **Health Check**: 集成 Spring Boot Actuator，实时监控 Prompt 文件解析状态，解析失败自动标记服务为 DOWN。
- **Lazy Load (懒加载)**：支持缓存未命中时回源读取，提升冷启动速度。

---

## 📦 安装 (Installation)

在你的 Maven 项目中引入 Starter 依赖：

```xml
<dependency>
    <groupId>com.chih.JPrompt</groupId>
    <artifactId>JPrompt-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 🚀 快速开始 (Quick Start)

### 1. 开启自动扫描
在启动类上添加 `@PromptScan` 注解，指定 Mapper 接口所在的包。

```java
@SpringBootApplication
@PromptScan("com.example.demo.mapper")
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

### 2. 定义 Prompt 文件

**方式 A：YAML 格式** (`src/main/resources/prompts/hello.yaml`)

```yaml
hello_user:
  model: gpt-3.5-turbo
  template: "Hello {{name}}, welcome to JPrompt!"
```

**方式 B：Markdown 格式** (`src/main/resources/prompts/code_review.md`)

````markdown
---
id: code_review
model: gpt-4
temperature: 0.2
---
You are a Senior Java Architect.
Please review the following code:
```java
{{code}}
```
````

### 3. 定义 Mapper 接口
使用 `@PromptMapper` 标记接口，使用 `@Prompt` 关联文件中的 Key。

```java
@PromptMapper
public interface MyAiMapper {

    @Prompt("hello_user")
    String sayHello(@Param("name") String name);

    @Prompt("code_review")
    String reviewCode(@Param("code") String code);
}
```

### 4. 注入使用
```java
@Service
public class MyService {
    @Autowired
    private MyAiMapper aiMapper;

    public void run() {
        String prompt = aiMapper.sayHello("Developer");
        System.out.println(prompt);
        // Output: Hello Developer, welcome to JPrompt!
    }
}
```

---

## ⚙️ 高级配置 (Configuration)

在 `application.yml` 中配置扫描路径。

```yaml
j-prompt:
  # 热更新防抖时间 (毫秒)
  debounce-millis: 500
  locations:
    # 默认扫描路径 (Jar包内)
    - "classpath*:prompts/**/*.yaml"
    - "classpath*:prompts/**/*.md"
    # 添加外部路径以支持生产环境热更新
    - "file:./config/prompts/*.yaml"
    - "file:./config/prompts/*.md"
```

### 运维监控 (Ops)

**Metrics (Prometheus/Grafana)**:
- `jprompt.render.timer`: 渲染耗时
- `jprompt.render.count`: 调用次数

**Health Check (/actuator/health)**:
如果部分 Prompt 文件解析失败，健康状态将变为 `DOWN`，并显示错误详情：
```json
"jPrompt": {
    "status": "DOWN",
    "details": {
        "message": "Some prompt files failed to load.",
        "errors": { "bad.yaml": "Syntax Error..." }
    }
}
```

---

## 🏗️ 架构设计

项目采用 Maven 多模块架构：

- **JPrompt-core**: 核心引擎。包含注解、SPI 接口、Mustache 实现、异常体系。**零 Spring 依赖**。
- **JPrompt-spring-boot-starter**: Spring 集成层。实现自动配置、资源扫描、NIO 热更新监听、Micrometer 适配、Health Indicator。
- **JPrompt-demo**: 示例项目。

### SPI 扩展能力
JPrompt 允许你替换核心组件：
- `PromptSource`: 自定义 Prompt 来源（如 Nacos, Database）。
- `TemplateEngine`: 自定义模板引擎（如 Freemarker, Velocity）。
- `PromptMetrics`: 自定义监控埋点。

---

## 📝 待办事项 (Roadmap)

- [x] SPI 核心架构与并发安全
- [x] **高性能增量热更新 (Diff + Batch)**
- [x] **编译期依赖追踪与级联更新**
- [x] **Caffeine 缓存与低内存架构**
- [x] Spring Boot Starter & 自动扫描
- [x] Mustache 模板引擎集成 (支持 Partials)
- [x] Markdown (FrontMatter) 格式支持
- [x] Micrometer 可观测性集成
- [x] Health Check 健康检查集成
- [x] 核心单元测试与异常体系
- [ ] 发布至 Maven Central
- [ ] 支持更多模板引擎扩展包 (Freemarker 等)
- [ ] 增加 Nacos/Apollo 配置中心支持适配器

---

## 🤝 贡献 (Contributing)

欢迎提交 Issue 和 Pull Request！

## 📄 License

Apache License 2.0