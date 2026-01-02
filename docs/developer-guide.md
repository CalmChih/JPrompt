# JPrompt 开发者指南

本指南面向希望参与 JPrompt 项目开发、贡献代码或深入理解框架原理的开发者。

## 目录

- [开发环境设置](#开发环境设置)
- [项目构建](#项目构建)
- [代码结构](#代码结构)
- [开发规范](#开发规范)
- [测试指南](#测试指南)
- [性能优化](#性能优化)
- [调试技巧](#调试技巧)
- [发布流程](#发布流程)

---

## 开发环境设置

### 必需环境

1. **JDK 17+**
   ```bash
   java -version  # 确保版本 >= 17
   ```

2. **Maven 3.6+**
   ```bash
   mvn -version  # 确保版本 >= 3.6
   ```

3. **IDE 推荐**
   - IntelliJ IDEA (推荐)
   - Eclipse
   - VS Code + Java Extension Pack

### IDE 配置

#### IntelliJ IDEA

1. **安装插件**
   - Lombok Plugin
   - Maven Helper
   - CamelCase (可选)

2. **配置设置**
   - Settings → Build → Compiler → Annotation Processors
     - ✅ Enable annotation processing
   - Settings → Build → Build Tools → Maven
     - Maven home directory: 指向本地 Maven 安装路径
     - User settings file: ~/.m2/settings.xml

3. **代码样式**
   - 导入 Google Java Style Guide 配置
   - Settings → Editor → Code Style → Java → Import Scheme

#### Eclipse

1. **安装插件**
   - M2Eclipse (Maven 集成)
   - Lombok Installer
   - Spring Tools

2. **配置**
   - Window → Preferences → Java → Compiler
     - Compiler compliance level: 17
   - Window → Preferences → Maven
     - User Settings: ~/.m2/settings.xml

---

## 项目构建

### 克隆项目

```bash
git clone https://github.com/CalmChih/JPrompt.git
cd JPrompt
```

### 构建命令

```bash
# 清理并编译
mvn clean compile

# 运行测试
mvn test

# 打包(跳过测试)
mvn clean package -DskipTests

# 完整构建(包含测试和文档)
mvn clean install

# 跳过检查(快速构建)
mvn clean install -DskipTests -Dcheckstyle.skip
```

### 本地安装

将项目安装到本地 Maven 仓库,以便其他项目引用:

```bash
mvn clean install
```

### 运行 Demo

```bash
# 方式一: 使用 Maven 插件
mvn spring-boot:run -pl JPrompt-demo

# 方式二: 先打包再运行
mvn clean package -pl JPrompt-demo
java -jar JPrompt-demo/target/JPrompt-demo-1.0.0.jar
```

---

## 代码结构

### 模块组织

```
JPrompt/
├── JPrompt-core/                    # 核心引擎模块(零 Spring 依赖)
│   ├── src/main/java/
│   │   └── com/chih/JPrompt/core/
│   │       ├── annotation/          # 注解定义
│   │       ├── engine/              # 核心引擎
│   │       ├── spi/                 # SPI 接口
│   │       ├── impl/                # 核心实现
│   │       ├── domain/              # 领域模型
│   │       ├── support/             # 支持类
│   │       └── exception/           # 异常体系
│   └── src/test/java/               # 单元测试
│
├── JPrompt-spring-boot-starter/     # Spring Boot 集成
│   ├── src/main/java/
│   │   └── com/chih/JPrompt/spring/
│   │       ├── scan/                # Mapper 扫描
│   │       ├── health/              # 健康检查
│   │       └── metrics/             # 监控指标
│   └── src/test/java/               # 集成测试
│
└── JPrompt-demo/                    # 演示应用
    ├── src/main/java/
    │   └── com/chih/JPrompt/demo/
    │       ├── mapper/              # 示例 Mapper
    │       ├── controller/          # REST API
    │       └── dto/                 # 数据传输对象
    └── src/main/resources/
        └── prompts/                 # 提示词模板
```

### 包职责说明

#### JPrompt-core

**annotation/** - 注解定义
- `@PromptMapper` - 标记 Mapper 接口
- `@Prompt` - 关联 Prompt Key
- `@Param` - 指定参数名

**engine/** - 核心引擎
- `PromptManager` - Prompt 管理器,协调 Source 和 Cache
- `PromptMapperFactory` - 动态代理工厂

**spi/** - SPI 扩展接口
- `PromptSource` - Prompt 来源接口
- `TemplateEngine` - 模板引擎接口
- `PromptMetrics` - 监控指标接口

**impl/** - 核心实现
- `FilePromptSource` - 文件系统 Prompt 源
- `MustacheTemplateEngine` - Mustache 模板引擎
- `NoOpPromptMetrics` - 空操作的监控实现

**domain/** - 领域模型
- `PromptMeta` - Prompt 元数据

**support/** - 支持类
- `FileResource` - 文件资源封装
- `PromptParser` - Prompt 解析器

**exception/** - 异常体系
- `PromptNotFoundException` - Prompt 未找到异常
- `PromptRenderException` - Prompt 渲染异常

#### JPrompt-spring-boot-starter

**scan/** - Mapper 扫描机制
- `ClassPathPromptMapperScanner` - 类路径扫描器
- `PromptFactoryBean` - Mapper 工厂 Bean

**health/** - 健康检查
- `JPromptHealthIndicator` - 健康检查指示器

**metrics/** - 监控指标
- `MicrometerPromptMetrics` - Micrometer 指标适配

**自动配置**
- `PromptAutoConfiguration` - 核心自动配置类
- `JPromptProperties` - 配置属性绑定

---

## 开发规范

### 代码风格

遵循 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)

**关键规范**:
1. **缩进**: 使用 2 个空格(不使用 Tab)
2. **行宽**: 每行最多 100 个字符
3. **命名**:
   - 类名: UpperCamelCase (如 `PromptManager`)
   - 方法名: lowerCamelCase (如 `renderPrompt`)
   - 常量: UPPER_SNAKE_CASE (如 `MAX_SIZE`)
   - 包名: 全小写,点分隔 (如 `com.chih.JPrompt.core`)
4. **大括号**:
   - 左大括号不换行
   - 右大括号换行
   - 即使只有一条语句也使用大括号

**示例**:
```java
// ✅ 正确
public class Example {
    private static final int MAX_SIZE = 100;

    public void process(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        // ...
    }
}

// ❌ 错误
public class example {
    private static final int max_size = 100;

    public void process(String input) {
        if (input == null) throw new IllegalArgumentException();
    }
}
```

### 注释规范

#### JavaDoc 注释

所有公共类、方法、字段都必须包含 JavaDoc 注释。

**格式**:
```java
/**
 * 简洁的一句话描述。
 *
 * <p>详细说明(可选)。</p>
 *
 * <h3>使用示例:</h3>
 * <pre>{@code
 * // 示例代码
 * Example.example();
 * }</pre>
 *
 * @param paramName 参数说明
 * @return 返回值说明
 * @throws ExceptionName 异常说明
 * @author 作者名
 * @since 版本号
 */
public String exampleMethod(String paramName) throws Exception {
    // 实现
}
```

#### 行内注释

使用 `//` 进行单行注释,使用 `/* */` 进行多行注释。

**示例**:
```java
// 1. 创建缓存
Cache<String, String> cache = Caffeine.newBuilder().build();

// 2. 加载数据
String data = loadData();

/*
 * 复杂的逻辑说明
 * 包含多行解释
 */
for (String item : items) {
    process(item);
}
```

### 提交规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范。

**格式**:
```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type 类型**:
- `feat` - 新功能
- `fix` - Bug 修复
- `docs` - 文档更新
- `style` - 代码格式调整
- `refactor` - 重构(不改变功能)
- `test` - 测试相关
- `chore` - 构建/工具链相关

**示例**:
```
feat(core): add support for partial templates

- Implement {{> partial}} syntax in Mustache engine
- Add dependency tracking for cascading updates
- Update documentation with examples

Closes #123
```

---

## 测试指南

### 测试结构

```
src/test/java/
└── com/chih/JPrompt/core/
    ├── engine/              # 引擎测试
    │   ├── PromptManagerTest.java
    │   └── PromptMapperFactoryTest.java
    ├── spi/                 # SPI 测试
    │   └── TemplateEngineTest.java
    └── integration/         # 集成测试
        └── HotReloadTest.java
```

### 单元测试

**命名规范**: `类名 + Test.java`

**示例**:
```java
class PromptManagerTest {

    private PromptManager manager;
    private PromptSource mockSource;

    @BeforeEach
    void setUp() {
        mockSource = mock(PromptSource.class);
        manager = new PromptManager(mockSource);
    }

    @Test
    @DisplayName("应该成功渲染 Prompt")
    void shouldRenderPromptSuccessfully() {
        // Given
        String key = "greeting";
        Map<String, Object> vars = Map.of("name", "Alice");

        // When
        String result = manager.render(key, vars);

        // Then
        assertThat(result).contains("Alice");
    }

    @Test
    @DisplayName("当 Prompt 不存在时应该抛出异常")
    void shouldThrowExceptionWhenPromptNotFound() {
        // Given
        String key = "nonexistent";

        // When & Then
        assertThatThrownBy(() -> manager.render(key, Map.of()))
            .isInstanceOf(PromptNotFoundException.class)
            .hasMessageContaining(key);
    }
}
```

### 集成测试

**命名规范**: 功能描述 + `IntegrationTest.java`

**示例**:
```java
@SpringBootTest
class HotReloadIntegrationTest {

    @Autowired
    private PromptManager manager;

    @Test
    @DisplayName("应该支持热更新 Prompt")
    void shouldSupportHotReload() throws IOException, InterruptedException {
        // Given
        Path promptFile = Paths.get("./prompts/test.yaml");
        String originalContent = Files.readString(promptFile);

        // When
        modifyFile(promptFile, "updated content");
        Thread.sleep(1000); // 等待热更新生效

        // Then
        String result = manager.render("test", Map.of());
        assertThat(result).contains("updated");

        // Cleanup
        Files.writeString(promptFile, originalContent);
    }
}
```

### 并发测试

使用 `JUnit 5` 并发测试和 `Awaitility` 等待异步操作。

**示例**:
```java
@Test
@DisplayName("应该支持并发渲染")
void shouldSupportConcurrentRendering() throws InterruptedException {
    // Given
    int threadCount = 10;
    int callsPerThread = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    // When
    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                for (int j = 0; j < callsPerThread; j++) {
                    manager.render("greeting", Map.of("name", "User" + j));
                }
            } finally {
                latch.countDown();
            }
        });
    }

    // Then
    assertTrue(latch.await(30, TimeUnit.SECONDS));
    executor.shutdown();
}
```

### 测试覆盖率

目标: 核心模块测试覆盖率 > 85%

**查看覆盖率**:
```bash
mvn clean test jacoco:report
# 报告路径: target/site/jacoco/index.html
```

---

## 性能优化

### 性能测试

使用 JMH (Java Microbenchmark Harness) 进行微基准测试。

**依赖配置**:
```xml
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
```

**基准测试示例**:
```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class PromptManagerBenchmark {

    private PromptManager manager;
    private Map<String, Object> variables;

    @Setup
    public void setup() {
        PromptSource source = new FilePromptSource("/prompts");
        manager = new PromptManager(source);
        variables = Map.of("name", "Alice");
    }

    @Benchmark
    public String benchmarkRender() {
        return manager.render("greeting", variables);
    }
}
```

**运行基准测试**:
```bash
mvn clean test -jar target/benchmarks.jar
```

### 性能优化建议

1. **减少锁竞争**
   - 使用读写锁替代全局 synchronized
   - 缩小锁的范围
   - 使用并发集合(ConcurrentHashMap)

2. **缓存优化**
   - 使用 Caffeine 替代 Guava Cache
   - 配置合理的缓存大小和过期策略
   - 避免缓存雪崩

3. **内存优化**
   - 使用 Index-Only 模式,避免缓存原始内容
   - 及时释放大对象
   - 使用对象池减少 GC 压力

4. **IO 优化**
   - 使用 NIO 进行文件操作
   - 批量读取减少 IO 次数
   - 使用缓冲流

---

## 调试技巧

### 日志配置

在 `application.yml` 中配置日志级别:

```yaml
logging:
  level:
    com.chih.JPrompt: DEBUG
    com.chih.JPrompt.core.engine: TRACE
    com.chih.JPrompt.core.impl: DEBUG
```

### 远程调试

**启动参数**:
```bash
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar app.jar
```

**IDE 配置**:
- Run → Edit Configurations → Remote → Debug
- Host: localhost, Port: 5005

### 常用断点技巧

1. **条件断点**: 只在满足条件时暂停
   ```
   右键断点 → Edit Breakpoint → Condition: variables.size() > 100
   ```

2. **日志断点**: 打印日志而不暂停
   ```
   右键断点 → Edit Breakpoint → Log evaluated expression
   ```

3. **异常断点**: 捕获特定异常
   ```
   Run → View Breakpoints → Java Exception Breakpoints
   添加: PromptNotFoundException
   ```

---

## 发布流程

### 版本号规范

遵循 [Semantic Versioning 2.0.0](https://semver.org/)

格式: `MAJOR.MINOR.PATCH`

- **MAJOR**: 不兼容的 API 变更
- **MINOR**: 向后兼容的功能新增
- **PATCH**: 向后兼容的 Bug 修复

示例: `1.0.0`, `1.1.0`, `1.1.1`

### 发布步骤

1. **更新版本号**
   ```bash
   # 更新所有模块的 pom.xml
   mvn versions:set -DnewVersion=1.0.1
   ```

2. **更新文档**
   - 更新 README.md 中的版本号
   - 更新 CHANGELOG.md

3. **运行测试**
   ```bash
   mvn clean test
   ```

4. **打包**
   ```bash
   mvn clean package -DskipTests
   ```

5. **发布到 Maven Central**
   ```bash
   mvn deploy -P release
   ```

6. **打标签**
   ```bash
   git tag -a v1.0.1 -m "Release version 1.0.1"
   git push origin v1.0.1
   ```

7. **发布 GitHub Release**
   - 在 GitHub 上创建 Release
   - 上传构建产物
   - 编写 Release Notes

---

## 常见问题

### Q: 如何调试热更新功能?

A: 使用文件监听日志和断点:
```yaml
logging:
  level:
    com.chih.JPrompt.core.impl.FilePromptSource: TRACE
```

在 `PromptManager.handleIncrementalUpdate` 方法设置断点。

### Q: 如何测试并发性能?

A: 使用 JMH 基准测试或 JUnit 并发测试:
```java
ExecutorService executor = Executors.newFixedThreadPool(10);
// ... 并发测试逻辑
```

### Q: 如何添加新的 SPI 实现?

A: 实现 SPI 接口并注册为 Spring Bean:
```java
@Component
public class CustomPromptSource implements PromptSource {
    // ...
}
```

---

## 相关资源

- [项目主页](../README.md)
- [API 参考文档](api.md)
- [扩展开发指南](extension-guide.md)
- [配置参考手册](configuration.md)
- [GitHub Issues](https://github.com/CalmChih/JPrompt/issues)
