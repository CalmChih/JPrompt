# JPrompt - Java Prompt Mapper

> The "MyBatis" for LLM Prompts.
> 像管理 SQL 一样管理你的 AI 提示词。

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-green.svg)]()
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()

**JPrompt** 是一个专为 Java/Spring 开发者设计的生产级 Prompt（提示词）管理框架。它旨在解决 Prompt 硬编码在 Java 字符串中难以维护、无法版本控制、无法热更新的痛点。

---

## ✨ 核心特性 (Key Features)

- **🚀 接口化调用**：类似 MyBatis 的 Mapper 接口设计 (`@PromptMapper`)，自动生成代理实现，无需编写样板代码。
- **🔄 高性能热更新**：
  - 基于 **Java NIO WatchService** 实现文件监听，**毫秒级**响应。
  - 支持 **增量更新 (Incremental Update)**，仅重载变动文件，极大降低 IO 开销。
  - 内置 **防抖 (Debounce)** 机制，完美处理编辑器保存时的事件抖动。
- **⚡️ 预编译与智能复用**：
  - 启动时预编译 Template，运行时 **零解析开销**。
  - 热更新时智能比对内容，内容未变则复用旧对象，减少内存抖动。
- **📝 多格式支持**：
  - `.yaml`: 适合集中管理短文本提示词。
  - `.md` (Markdown): 支持 **FrontMatter** 元数据，适合编写包含代码块、Few-Shot 示例的复杂 Prompt。
- **🧠 高级模板引擎**：内置 Mustache 引擎，支持对象属性访问 (`{{user.name}}`)、列表循环、逻辑判断。
- **📊 可观测性 (Observability)**：自动适配 **Micrometer**。如果环境中有 Actuator，自动暴露 `jprompt.render.timer` 和 `jprompt.render.count` 监控指标。
- **🛡 生产级健壮性**：
  - **Fail-Fast**: 启动时校验配置，发现错误直接阻止启动，防止带病上线。
  - **Copy-On-Write**: 核心缓存采用写时复制机制，确保高并发下的读取绝对安全。
  - **完整异常体系**: 提供 `PromptNotFoundException`, `PromptParseException` 等精确异常。

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

**方式 A：YAML 格式 (适合短文本)**
在 `src/main/resources/prompts/` 目录下创建 `hello.yaml`：

```yaml
hello_user:
  model: gpt-3.5-turbo
  template: "Hello {{name}}, welcome to JPrompt!"
```

**方式 B：Markdown 格式 (适合长文本)**
在同一目录下创建 `code_review.md`（文件名即为 Prompt Key）：

```markdown
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
```

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

在 `application.yml` 中配置扫描路径。支持 `classpath:` (只读) 和 `file:` (热更新) 混合使用。

```yaml
prompt:
  locations:
    # 默认扫描路径 (Jar包内)
    - "classpath*:prompts/**/*.yaml"
    - "classpath*:prompts/**/*.md"
    # 添加外部路径以支持生产环境热更新
    - "file:./config/prompts/*.yaml"
    - "file:./config/prompts/*.md"
```

### 监控指标 (Metrics)
如果引入了 `spring-boot-starter-actuator`，JPrompt 会自动暴露以下 Metrics：

- `jprompt.render.timer`: 渲染耗时 (Timer)
- `jprompt.render.count`: 调用次数 (Counter)

Tag 包含 `prompt` (key) 和 `result` (success/failure)。

---

## 🏗️ 架构设计

项目采用 Maven 多模块架构：

- **JPrompt-core**: 核心引擎。包含注解、SPI 接口、Mustache 实现、异常体系。**零 Spring 依赖**。
- **JPrompt-spring-boot-starter**: Spring 集成层。实现自动配置、资源扫描、NIO 热更新监听、Micrometer 适配。
- **JPrompt-demo**: 示例项目。

### SPI 扩展能力
JPrompt 允许你替换核心组件：
- `PromptSource`: 自定义 Prompt 来源（如 Nacos, Database）。
- `TemplateEngine`: 自定义模板引擎（如 Freemarker, Velocity）。
- `PromptMetrics`: 自定义监控埋点。

---

## 📝 待办事项 (Roadmap)

- [x] SPI 核心架构与并发安全
- [x] Spring Boot Starter & 自动扫描
- [x] Mustache 模板引擎集成
- [x] Markdown (FrontMatter) 格式支持
- [x] 高性能文件热更新 (NIO + 防抖)
- [x] Micrometer 可观测性集成
- [x] 核心单元测试与异常体系
- [ ] 发布至 Maven Central
- [ ] 支持更多模板引擎扩展包 (Freemarker 等)
- [ ] 增加 Nacos/Apollo 配置中心支持适配器

---

## 🤝 贡献 (Contributing)

欢迎提交 Issue 和 Pull Request！

## 📄 License

Apache License 2.0