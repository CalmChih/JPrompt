# JPrompt 配置参考手册

本手册提供 JPrompt 框架的详细配置说明,包括所有配置参数、示例和最佳实践。

## 目录

- [配置概览](#配置概览)
- [基础配置](#基础配置)
- [资源位置配置](#资源位置配置)
- [热更新配置](#热更新配置)
- [缓存配置](#缓存配置)
- [监控配置](#监控配置)
- [高级配置](#高级配置)
- [环境配置](#环境配置)

---

## 配置概览

JPrompt 通过 `application.yml` 或 `application.properties` 进行配置,使用 `j-prompt` 作为前缀。

### 配置文件位置

```
src/main/resources/
├── application.yml              # 主配置文件
├── application-dev.yml          # 开发环境配置
├── application-test.yml         # 测试环境配置
└── application-prod.yml         # 生产环境配置
```

### 配置优先级

1. 命令行参数
2. 环境变量
3. Profile-specific 配置文件
4. 主配置文件
5. 默认值

---

## 基础配置

### 最小配置

**application.yml**:

```yaml
j-prompt:
  locations:
    - "classpath*:prompts/**/*.yaml"
```

**说明**:
- 使用默认配置即可运行 JPrompt
- 自动扫描类路径下的 `prompts` 目录
- 支持 YAML 格式的 Prompt 文件

### 完整配置

**application.yml**:

```yaml
j-prompt:
  # 资源位置配置
  locations:
    - "classpath*:prompts/**/*.yaml"
    - "classpath*:prompts/**/*.md"
    - "file:./config/prompts/*.yaml"
    - "file:./config/prompts/*.md"

  # 热更新配置
  debounce-millis: 500

  # 线程池配置(可选)
  thread-pool:
    watcher-size: 1
    debounce-size: 1
```

---

## 资源位置配置

### locations 配置

`locations` 是一个字符串列表,支持多个资源位置。

**支持的位置协议**:

| 协议 | 说明 | 示例 | 热更新 |
|------|------|------|--------|
| `classpath*:` | 扫描所有 JAR 包 | `classpath*:prompts/*.yaml` | ❌ |
| `classpath:` | 仅扫描当前 Classpath | `classpath:prompts/*.yaml` | ❌ |
| `file:` | 文件系统路径 | `file:./prompts/*.yaml` | ✅ |
| (无协议) | 相对/绝对路径 | `/opt/prompts/*.yaml` | ✅ |

### 配置示例

#### 示例 1: 开发环境配置

```yaml
j-prompt:
  locations:
    # 1. 类路径资源(JAR 包内,只读)
    - "classpath*:prompts/**/*.yaml"

    # 2. 项目根目录(支持热更新,方便开发调试)
    - "file:./prompts.yaml"
    - "file:./prompts/*.yaml"
```

#### 示例 2: 生产环境配置

```yaml
j-prompt:
  locations:
    # 1. 内置默认提示词(只读)
    - "classpath*:prompts/**/*.yaml"

    # 2. 外部配置卷(支持热更新,运维管理)
    - "file:/opt/app/config/prompts/*.yaml"
    - "file:/opt/app/config/prompts/**/*.md"

    # 3. 共享存储(多实例共享提示词)
    - "file:/mnt/shared-prompts/*.yaml"
```

#### 示例 3: 多环境配置

```yaml
# application-dev.yml
j-prompt:
  locations:
    - "classpath*:prompts/**/*.yaml"
    - "file:./dev-prompts/*.yaml"

---
# application-test.yml
j-prompt:
  locations:
    - "classpath*:prompts/**/*.yaml"
    - "file:./test-prompts/*.yaml"

---
# application-prod.yml
j-prompt:
  locations:
    - "classpath*:prompts/**/*.yaml"
    - "file:/opt/config/prompts/*.yaml"
```

### 路径模式说明

#### Ant-style 风格

支持 Ant 风格的路径模式匹配:

| 模式 | 说明 | 示例 |
|------|------|------|
| `*` | 匹配单层路径 | `prompts/*.yaml` |
| `**` | 匹配多层路径 | `prompts/**/*.yaml` |
| `?` | 匹配单个字符 | `prompt?.yaml` |
| `*.yaml` | 匹配所有 .yaml 文件 | `*.yaml` |

**示例**:

```yaml
j-prompt:
  locations:
    # 匹配 prompts 目录下所有 .yaml 文件
    - "classpath*:prompts/*.yaml"

    # 匹配 prompts 及子目录下所有 .yaml 文件
    - "classpath*:prompts/**/*.yaml"

    # 匹配多个目录
    - "classpath*:chat/**/*.yaml"
    - "classpath*:analysis/**/*.yaml"
```

---

## 热更新配置

### debounce-millis 配置

防抖延迟时间,单位毫秒。用于避免编辑器保存时触发多次文件事件。

**默认值**: `500` 毫秒

**建议值**:
- 开发环境: `300-500` ms (快速响应)
- 生产环境: `1000-2000` ms (稳定优先)

**配置示例**:

```yaml
j-prompt:
  debounce-millis: 1000  # 1秒防抖
```

**工作原理**:

```
文件保存事件
    ↓
┌─────────────────────────────────┐
│  防抖窗口 (debounce-millis)      │
│                                 │
│  事件1 ━━━○                      │
│  事件2 ━━━━━○                     │
│  事件3 ━━━━━━━○                   │
│                                 │
│  仅在最后一次事件后触发更新       │
└─────────────────────────────────┘
         ↓
    执行热更新
```

### 热更新监控

启用热更新日志监控:

```yaml
logging:
  level:
    com.chih.JPrompt.core.impl: DEBUG
```

**日志示例**:

```log
2025-01-02 10:15:30.123 DEBUG [jprompt-watcher-1] File change detected: /opt/prompts/greeting.yaml
2025-01-02 10:15:30.623 INFO  [jprompt-debouncer-1] Hot update started
2025-01-02 10:15:30.750 INFO  [jprompt-debouncer-1] Hot update completed: 1 prompts updated, 2 dependencies refreshed
```

---

## 缓存配置

JPrompt 内部使用 Caffeine 高性能缓存,可通过配置调整缓存行为。

### PromptManager 缓存配置

当前版本中,PromptManager 的缓存配置为固定值,未来版本将支持外部配置。

**默认配置**:

```java
.cache = Caffeine.newBuilder()
    .maximumSize(10_000)              // 最大缓存条目数
    .expireAfterAccess(24, HOURS)     // 24小时未访问自动过期
    .recordStats()                    // 记录缓存统计
    .build();
```

**缓存统计查看**:

```java
@Autowired
private PromptManager manager;

public void printCacheStats() {
    CacheStats stats = manager.getCacheStats();
    System.out.println("命中率: " + stats.hitRate());
    System.out.println("加载次数: " + stats.totalLoadCount());
}
```

---

## 监控配置

### Micrometer 集成

JPrompt 自动适配 Micrometer 监控指标。

**依赖**:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**配置**:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

**监控指标**:

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `jprompt.render.timer` | Timer | 渲染耗时 |
| `jprompt.render.count` | Counter | 渲染次数 |
| `jprompt.render.errors` | Counter | 渲染错误次数 |

**访问端点**:

```bash
# Prometheus 指标
curl http://localhost:8080/actuator/prometheus

# 自定义指标
curl http://localhost:8080/actuator/metrics/jprompt.render.timer
```

### 健康检查配置

**依赖**:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**配置**:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

**健康检查状态**:

```bash
curl http://localhost:8080/actuator/health
```

**响应示例**:

```json
{
  "status": "UP",
  "components": {
    "jPrompt": {
      "status": "UP",
      "details": {
        "message": "All prompts loaded successfully."
      }
    }
  }
}
```

---

## 高级配置

### 自定义线程池

自定义文件监听和防抖线程池(可选):

```java
@Configuration
public class JPromptConfiguration {

    @Bean("jPromptWatcherExecutor")
    public ExecutorService jPromptWatcherExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);  // 修改为 2 个线程
        executor.setMaxPoolSize(2);
        executor.setThreadNamePrefix("custom-jprompt-watcher-");
        executor.setDaemon(true);
        executor.initialize();
        return executor.getThreadPoolExecutor();
    }

    @Bean("jPromptDebounceExecutor")
    public ScheduledExecutorService jPromptDebounceExecutor() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);  // 修改为 2 个线程
        scheduler.setThreadNamePrefix("custom-jprompt-debouncer-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler.getScheduledExecutor();
    }
}
```

### 自定义组件

覆盖默认组件实现:

```java
@Configuration
public class JPromptExtensionConfiguration {

    // 自定义 PromptSource
    @Bean
    @Primary
    public PromptSource customPromptSource() {
        return new DatabasePromptSource(dataSource);
    }

    // 自定义 TemplateEngine
    @Bean
    @Primary
    public TemplateEngine customTemplateEngine() {
        return new FreemarkerTemplateEngine();
    }

    // 自定义 PromptMetrics
    @Bean
    @Primary
    public PromptMetrics customPromptMetrics() {
        return new CustomMetrics();
    }
}
```

---

## 环境配置

### 开发环境

**application-dev.yml**:

```yaml
j-prompt:
  locations:
    # 快速响应的本地文件
    - "file:./prompts/*.yaml"
    - "file:./dev-prompts/*.yaml"

  debounce-millis: 300  # 快速热更新

logging:
  level:
    com.chih.JPrompt: DEBUG
```

**特点**:
- 使用本地文件系统,支持热更新
- 较短的防抖延迟,快速响应变更
- 详细的日志输出

### 测试环境

**application-test.yml**:

```yaml
j-prompt:
  locations:
    - "classpath*:prompts/**/*.yaml"
    - "file:./test-prompts/*.yaml"

  debounce-millis: 500

logging:
  level:
    com.chih.JPrompt: INFO
```

**特点**:
- 混合使用类路径和本地文件
- 适中的防抖延迟
- INFO 级别日志

### 生产环境

**application-prod.yml**:

```yaml
j-prompt:
  locations:
    # 内置默认提示词
    - "classpath*:prompts/**/*.yaml"

    # 外部配置卷(运维管理)
    - "file:/opt/app/config/prompts/*.yaml"
    - "file:/opt/app/config/prompts/**/*.md"

    # 共享存储
    - "file:/mnt/nfs/prompts/*.yaml"

  debounce-millis: 2000  # 稳定优先

logging:
  level:
    com.chih.JPrompt: WARN

  # 生产环境关闭 DEBUG 日志
```

**特点**:
- 支持外部配置卷挂载
- 较长的防抖延迟,避免频繁更新
- 仅记录 WARN 及以上级别日志

---

## 配置检查清单

### 部署前检查

- [ ] 资源路径配置正确
- [ ] 热更新防抖时间合理
- [ ] 外部文件路径存在且可访问
- [ ] 日志级别配置合理
- [ ] 监控和健康检查已启用
- [ ] 环境变量正确设置
- [ ] 线程池配置适合负载

### 运行时检查

- [ ] 日志显示所有 Prompt 加载成功
- [ ] 健康检查状态为 UP
- [ ] 监控指标正常输出
- [ ] 热更新功能正常工作
- [ ] 缓存命中率合理

---

## 常见问题

### Q1: 配置了多个 locations,如何确定优先级?

**A**: 后配置的覆盖先配置的。建议将只读资源(如 classpath)放在前面,可写资源(如 file)放在后面。

### Q2: 热更新不生效?

**A**: 检查以下几点:
1. 文件路径是否使用 `file:` 协议
2. 文件是否在监听的目录下
3. 日志中是否有文件变更事件
4. 防抖时间是否过长

### Q3: 如何禁用热更新?

**A**: 只使用 `classpath:` 协议的资源配置,不使用 `file:` 协议。

---

## 相关资源

- [API 参考文档](api.md)
- [开发者指南](developer-guide.md)
- [扩展开发指南](extension-guide.md)
- [项目主页](../README.md)
