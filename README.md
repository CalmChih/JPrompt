# JPrompt - Java Prompt Mapper

> The "MyBatis" for LLM Prompts.
> 像管理 SQL 一样管理你的 AI 提示词。

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x%2F4.x-green.svg)]()
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)]()
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Coverage](https://img.shields.io/badge/coverage-85%25-brightgreen.svg)]()
[![Maven Central](https://img.shields.io/badge/maven%20central-1.0.0-brightgreen)](https://central.sonatype.com/artifact/io.github.calmchih/JPrompt-spring-boot-starter)

**JPrompt** 是一个专为 Java/Spring 开发者设计的生产级 Prompt（提示词）管理框架。它旨在解决 Prompt 硬编码在 Java 字符串中难以维护、无法版本控制、无法热更新的痛点。

## 🎯 项目愿景

通过类似 MyBatis 的 Mapper 接口设计，让 Java 开发者能够优雅地管理和使用 AI 提示词，实现：
- **告别硬编码**：提示词与代码分离，支持版本控制
- **热更新能力**：生产环境无需重启即可更新提示词
- **高性能架构**：零 OOM 风险，支持高并发场景
- **开发友好**：IDE 智能提示，编译期检查

---

## ✨ 核心特性 (Key Features)

### 🚀 开发体验
- **接口化调用**：类似 MyBatis 的 Mapper 接口设计 (`@PromptMapper`)，自动生成代理实现，无需编写样板代码
- **注解驱动**：`@Prompt` 关联提示词Key，`@Param` 指定参数名，IDE 智能提示
- **多格式支持**：
  - `.yaml`: 适合集中管理短文本提示词，支持批量定义
  - `.md` (Markdown): 支持 **FrontMatter** 元数据，适合编写包含代码块、Few-Shot 示例的复杂 Prompt
- **模板继承**：支持 `{{> partial}}` 语法，实现提示词片段复用

### ⚡️ 高性能与低内存 (Performance & Memory)
- **极致内存优化 (Index-Only Pattern)**：
  - **Source 层**：采用"仅索引"策略，仅存储文件路径映射，**不缓存文件内容**，彻底杜绝 OOM 风险
  - **Manager 层**：集成 **Caffeine** 高性能缓存，支持 LRU 淘汰和最大容量控制
  - **Cache 瘦身**：编译后的模板对象自动丢弃原始字符串，减少 50%+ 堆内存占用
- **预编译机制**：启动时/热更时预编译 Template，运行时 **零解析开销**
- **并发安全**：使用读写锁和原子操作，支持高并发访问

### 🔄 智能热更新 (Intelligent Hot Reload)
- **精准增量更新 (Incremental Updates)**：基于文件监听的 Diff 计算，仅重编译发生变化的文件，拒绝全量重载
- **级联依赖更新 (Cascading Re-compilation)**：
  - 内置 **编译期依赖追踪** 和 **倒排索引 (Inverted Index)**
  - 当修改公共片段（如 `{{> common_header}}`）时，所有引用它的 Prompt 会自动检测并重编译
- **智能防抖 (Debouncing)**：支持变更暂存与批量推送，完美处理编辑器"全部保存"时的高频文件事件
- **多源支持**：同时支持文件系统和 Classpath 资源，JAR 包内资源也能热更新

### 🛡 生产级健壮性
- **可观测性 (Observability)**：
  - **Metrics**: 自动适配 Micrometer，暴露渲染耗时、成功率等指标
  - **Health Check**: 集成 Spring Boot Actuator，实时监控 Prompt 文件解析状态，解析失败自动标记服务为 DOWN
  - **详细日志**：结构化日志记录，包含热更新事件、错误信息等
- **容错机制**：单个文件解析失败不影响其他文件使用，优雅降级
- **Lazy Load (懒加载)**：支持缓存未命中时回源读取，提升冷启动速度

---

## 📦 安装 (Installation)

在你的 Maven 项目中引入 Starter 依赖：

```xml
<dependency>
    <groupId>io.github.calmchih</groupId>
    <artifactId>JPrompt-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

💡 **提示**：JPrompt 已发布至 [Maven Central](https://central.sonatype.com/artifact/io.github.calmchih/JPrompt-spring-boot-starter)，可直接使用最新版本。

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

    // 方法名与模板 key 一致时，可省略 @Prompt 注解
    String sayHello(String name);

    // 参数名与模板变量一致时，可省略 @Param 注解
    @Prompt("code_review")
    String reviewCode(String code);

    // 也可以显式指定参数名（当参数名与模板变量不一致时）
    @Prompt("hello_user")
    String greet(@Param("userName") String name);
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

### 模块结构
项目采用 Maven 多模块架构，职责清晰，依赖单向：

```
JPrompt/
├── JPrompt-core/                 # 🔧 核心引擎模块
│   ├── annotation/               # 注解定义 (@PromptMapper, @Prompt, @Param)
│   ├── engine/                   # 核心引擎 (PromptManager, PromptMapperFactory)
│   ├── spi/                      # SPI 接口 (PromptSource, TemplateEngine, PromptMetrics)
│   ├── impl/                     # 核心实现 (FilePromptSource, MustacheTemplateEngine)
│   ├── domain/                   # 领域模型 (PromptMeta, TemplateContext)
│   ├── support/                  # 支持类 (FileResource, PromptParser)
│   └── exception/                # 异常体系
│
├── JPrompt-spring-boot-starter/  # 🚀 Spring Boot 集成层
│   ├── auto/                     # 自动配置
│   ├── scan/                     # Mapper 扫描机制
│   ├── health/                   # 健康检查集成
│   ├── metrics/                  # 监控指标适配
│   └── config/                   # 配置属性绑定
│
└── JPrompt-demo/                 # 📖 演示应用
    ├── mapper/                   # 示例 Mapper 接口
    ├── dto/                      # 数据传输对象
    ├── controller/               # REST API 演示
    └── resources/                # 提示词模板文件
```

### 设计原则
- **分层架构**：Core 层零 Spring 依赖，可独立使用
- **开闭原则**：通过 SPI 支持扩展，核心功能对修改封闭
- **依赖倒置**：面向接口编程，便于测试和扩展
- **单一职责**：每个类职责明确，便于维护

### SPI 扩展能力
JPrompt 提供丰富的扩展点，支持按需替换核心组件：

```java
// 自定义 Prompt Source (如 Nacos, Database)
@Component
public class NacosPromptSource implements PromptSource {
    // 实现从 Nacos 配置中心加载提示词
}

// 自定义模板引擎 (如 Freemarker)
@Component
public class FreemarkerTemplateEngine implements TemplateEngine {
    // 实现基于 Freemarker 的模板渲染
}

// 自定义监控指标
@Component
public class CustomPromptMetrics implements PromptMetrics {
    // 实现自定义的监控埋点逻辑
}
```

---

## 📝 项目状态 (Project Status) - v1.0.0 已发布

### ✅ 已完成功能 (v1.0.0)
- [x] **核心架构**：SPI 核心架构与并发安全设计
- [x] **高性能缓存**：Caffeine 缓存与低内存架构 (Index-Only Pattern)
- [x] **智能热更新**：高性能增量热更新 + 依赖追踪与级联更新
- [x] **Spring 集成**：Spring Boot Starter & 自动扫描机制
- [x] **模板引擎**：Mustache 模板引擎集成 (支持 Partials 继承)
- [x] **多格式支持**：YAML + Markdown (FrontMatter) 格式支持
- [x] **可观测性**：Micrometer 可观测性集成
- [x] **健康检查**：Spring Boot Actuator 健康检查集成
- [x] **测试覆盖**：核心单元测试与集成测试 (并发性能测试)
- [x] **异常体系**：完善的异常处理和错误恢复机制
- [x] **演示项目**：完整的 Demo 应用和 REST API 示例
- [x] **Maven Central发布**：已发布至 Maven Central Repository (v1.0.0)

### 🚧 进行中 (In Progress)
- [ ] **文档完善**：API 文档和最佳实践指南
- [ ] **性能基准测试**：详细的性能测试报告

### 📋 计划功能 (Roadmap)
- [ ] **模板引擎扩展**：Freemarker、Velocity 等更多模板引擎支持
- [ ] **配置中心集成**：Nacos、Apollo 配置中心支持适配器
- [ ] **管理界面**：Web 管理控制台，支持在线编辑和预览
- [ ] **版本管理**：提示词版本控制和 A/B 测试支持
- [ ] **国际化**：多语言提示词支持

### 🎯 性能指标 (Performance Benchmarks)
- **内存占用**：相比传统方案减少 50%+ 内存使用
- **并发性能**：支持 10+ 线程并发访问，无性能衰减
- **热更新延迟**：文件变更后 500ms 内生效（可配置）
- **冷启动时间**：首次加载 < 100ms（缓存预热）

---

## 🚀 快速体验 (Quick Demo)

### 方式一：直接使用（推荐）
在你的项目中添加上述 Maven 依赖即可，JPrompt 已发布至 Maven Central。

### 方式二：运行演示项目
```bash
# 克隆项目
git clone https://github.com/CalmChih/JPrompt.git
cd JPrompt

# 构建项目
mvn clean install

# 运行演示
mvn spring-boot:run -pl JPrompt-demo
```

### 使用方式
应用启动后，可以通过编程方式体验 JPrompt 功能：

```java
@Autowired
private DemoMapper mapper;

// 简单问候
String greeting = mapper.sayHello("World");  // Hello World!

// 复杂对象分析
OrderDTO order = new OrderDTO();
// ... 设置订单信息
String analysis = mapper.analyzeOrder(order);

// 代码审查
String review = mapper.reviewCode("public static Map cache = new HashMap();");
```

### 热更新测试
1. 运行演示应用后，在代码中调用 `mapper.sayHello("Test")`
2. 修改 `JPrompt-demo/src/main/resources/prompts/prompts.yaml` 中 `sayHello` 的 `template` 内容
3. 再次调用方法，观察返回结果变化（无需重启应用）

### 监控端点
- 健康检查：http://localhost:8080/actuator/health
- 应用信息：http://localhost:8080/actuator/info
- 监控指标：http://localhost:8080/actuator/metrics

## 📚 文档导航 (Documentation)

- **[Demo 模块文档](JPrompt-demo/README.md)** - 详细的使用示例和 API 说明
- **[核心模块文档](JPrompt-core/CLAUDE.md)** - 核心架构和 API 文档
- **[Spring Boot 集成文档](JPrompt-spring-boot-starter/CLAUDE.md)** - 自动配置和扩展指南

## 🤝 贡献指南 (Contributing)

我们欢迎所有形式的贡献！

### 开发环境设置
1. **JDK 17+** 和 **Maven 3.6+**
2. IDE 推荐安装 **Lombok** 插件
3. 导入项目后运行 `mvn clean install` 构建依赖

### 贡献方式
- 🐛 **Bug Report**：通过 Issue 报告问题，请提供复现步骤
- 💡 **Feature Request**：通过 Issue 提出功能建议，详细描述使用场景
- 📝 **文档改进**：修正文档错误、补充示例代码
- 🔧 **代码贡献**：Fork 项目，提交 Pull Request

### 代码规范
- 遵循 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- 单元测试覆盖率 > 80%
- 提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范

## 📄 许可证 (License)

本项目采用 [Apache License 2.0](LICENSE) 开源协议。

---

<div align="center">

**⭐ 如果这个项目对你有帮助，请给一个 Star 支持我们！**

Made with ❤️ by JPrompt Team

</div>