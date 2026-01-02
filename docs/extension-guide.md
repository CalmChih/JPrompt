# JPrompt 扩展开发指南

本指南详细说明如何通过 JPrompt 的 SPI (Service Provider Interface) 机制扩展框架功能。

## 目录

- [SPI 架构概览](#spi-架构概览)
- [扩展 PromptSource](#扩展-promptsource)
- [扩展 TemplateEngine](#扩展-templateengine)
- [扩展 PromptMetrics](#扩展-promptmetrics)
- [完整示例](#完整示例)
- [最佳实践](#最佳实践)

---

## SPI 架构概览

JPrompt 采用 SPI (Service Provider Interface) 设计模式,核心组件全部基于接口定义,支持灵活替换和扩展。

### 核心 SPI 接口

```
┌─────────────────────────────────────────────────────┐
│                  JPrompt SPI 架构                    │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │PromptSource  │  │TemplateEngine│  │   Metrics │ │
│  │  (数据源)    │  │  (模板引擎)   │  │ (监控)    │ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬─────┘ │
│         │                 │                │        │
│         v                 v                v        │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │FilePrompt    │  │MustacheEng   │  │Micrometer │ │
│  │Source        │  │              │  │Metrics    │ │
│  └──────────────┘  └──────────────┘  └───────────┘ │
│                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │DatabasePrompt│  │FreemarkerEng │  │Custom     │ │
│  │Source        │  │              │  │Metrics    │ │
│  └──────────────┘  └──────────────┘  └───────────┘ │
│       (自定义)          (自定义)        (自定义)    │
└─────────────────────────────────────────────────────┘
```

### SPI 扩展的优势

1. **解耦合**: 核心功能与具体实现分离
2. **可测试**: 易于编写单元测试和 Mock
3. **可扩展**: 支持第三方实现
4. **可替换**: 运行时动态切换实现

---

## 扩展 PromptSource

PromptSource 负责从外部存储加载 Prompt 配置,支持扩展各种数据源。

### 接口定义

```java
public interface PromptSource extends AutoCloseable {
    /**
     * 加载所有的 Prompt 配置
     * @return Map<String, PromptMeta> key为promptId
     */
    Map<String, PromptMeta> loadAll();

    /**
     * 注册变更监听回调
     * 当源数据发生变化时,实现类需要主动调用 callback.run()
     * @param listener 回调函数
     */
    void onChange(Consumer<PromptChangeEvent> listener);

    /**
     * 按 ID 加载单个 Meta (用于 Manager 回源重编译)
     * @param key Prompt Key
     * @return PromptMeta,不存在时返回 null
     */
    default PromptMeta load(String key) {
        return loadAll().get(key);
    }

    /**
     * 关闭资源 (如线程池、WatchService 连接)
     */
    @Override
    default void close() throws Exception {
        // Default no-op
    }
}
```

### 示例 1: 从数据库加载

```java
package com.example.jprompt.extension;

import com.chih.JPrompt.core.domain.PromptMeta;
import com.chih.JPrompt.core.spi.PromptChangeEvent;
import com.chih.JPrompt.core.spi.PromptSource;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从数据库加载 Prompt 配置
 *
 * 数据库表结构:
 * CREATE TABLE prompts (
 *   id VARCHAR(255) PRIMARY KEY,
 *   model VARCHAR(100),
 *   temperature DOUBLE,
 *   max_tokens INT,
 *   timeout BIGINT,
 *   template TEXT,
 *   description TEXT
 * );
 */
public class DatabasePromptSource implements PromptSource {

    private static final Logger log = LoggerFactory.getLogger(DatabasePromptSource.class);

    private final HikariDataSource dataSource;
    private final Consumer<PromptChangeEvent> changeListener;
    private final Map<String, PromptMeta> cache = new ConcurrentHashMap<>();

    public DatabasePromptSource(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.changeListener = null; // 初始为 null,由 onChange 设置

        // 启动时全量加载
        loadAll();

        // 启动数据库变更监听(需要数据库支持)
        startDatabaseWatcher();
    }

    @Override
    public Map<String, PromptMeta> loadAll() {
        Map<String, PromptMeta> prompts = new HashMap<>();

        String sql = "SELECT id, model, temperature, max_tokens, timeout, template, description FROM prompts";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                PromptMeta meta = mapRowToPromptMeta(rs);
                prompts.put(meta.getId(), meta);
                cache.put(meta.getId(), meta);
            }

            log.info("Loaded {} prompts from database", prompts.size());

        } catch (SQLException e) {
            log.error("Failed to load prompts from database", e);
        }

        return prompts;
    }

    @Override
    public PromptMeta load(String key) {
        // 优先从缓存读取
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        // 缓存未命中,查询数据库
        String sql = "SELECT id, model, temperature, max_tokens, timeout, template, description " +
                     "FROM prompts WHERE id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, key);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                PromptMeta meta = mapRowToPromptMeta(rs);
                cache.put(key, meta);
                return meta;
            }

        } catch (SQLException e) {
            log.error("Failed to load prompt: {}", key, e);
        }

        return null;
    }

    @Override
    public void onChange(Consumer<PromptChangeEvent> listener) {
        // 存储监听器引用(使用反射或其他方式设置)
        // 这里简化处理,实际项目中可以使用 AtomicReference<Consumer>
        this.changeListener = listener;
    }

    /**
     * 监听数据库变更(需要数据库支持,如 PostgreSQL LISTEN/NOTIFY)
     */
    private void startDatabaseWatcher() {
        // 使用定时轮询或数据库监听机制
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            checkForUpdates();
        }, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * 检查数据库更新并触发变更事件
     */
    private void checkForUpdates() {
        Map<String, PromptMeta> currentData = loadAll();

        // 计算差异
        Set<String> updatedKeys = new HashSet<>();
        Set<String> removedKeys = new HashSet<>();
        Map<String, PromptMeta> updatedMetas = new HashMap<>();

        for (Map.Entry<String, PromptMeta> entry : currentData.entrySet()) {
            String key = entry.getKey();
            PromptMeta newMeta = entry.getValue();
            PromptMeta oldMeta = cache.get(key);

            if (oldMeta == null || !newMeta.getTemplate().equals(oldMeta.getTemplate())) {
                updatedKeys.add(key);
                updatedMetas.put(key, newMeta);
            }
        }

        for (String key : cache.keySet()) {
            if (!currentData.containsKey(key)) {
                removedKeys.add(key);
            }
        }

        // 触发变更事件
        if (!updatedKeys.isEmpty() || !removedKeys.isEmpty()) {
            PromptChangeEvent event = new PromptChangeEvent(updatedKeys, removedKeys, updatedMetas);

            if (changeListener != null) {
                changeListener.accept(event);
            }
        }

        // 更新缓存
        cache.clear();
        cache.putAll(currentData);
    }

    /**
     * 映射 ResultSet 行到 PromptMeta
     */
    private PromptMeta mapRowToPromptMeta(ResultSet rs) throws SQLException {
        PromptMeta meta = new PromptMeta();
        meta.setId(rs.getString("id"));
        meta.setModel(rs.getString("model"));
        meta.setTemperature(rs.getDouble("temperature"));
        meta.setMaxTokens(rs.getInt("max_tokens"));
        meta.setTimeout(rs.getLong("timeout"));
        meta.setTemplate(rs.getString("template"));
        meta.setDescription(rs.getString("description"));
        return meta;
    }

    @Override
    public void close() throws Exception {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
```

**Spring Boot 集成**:

```java
@Configuration
public class JPromptExtensionConfiguration {

    @Bean
    @Primary
    public PromptSource databasePromptSource(HikariDataSource dataSource) {
        return new DatabasePromptSource(dataSource);
    }
}
```

### 示例 2: 从 Nacos 配置中心加载

```java
package com.example.jprompt.extension;

import com.chih.JPrompt.core.domain.PromptMeta;
import com.chih.JPrompt.core.spi.PromptChangeEvent;
import com.chih.JPrompt.core.spi.PromptSource;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 从 Nacos 配置中心加载 Prompt 配置
 *
 * Nacos 配置格式 (YAML):
 * Data ID: jprompt-prompts.yaml
 * Group: JPROMPT_GROUP
 */
public class NacosPromptSource implements PromptSource {

    private static final Logger log = LoggerFactory.getLogger(NacosPromptSource.class);

    private final ConfigService configService;
    private final String dataId;
    private final String group;
    private final Map<String, PromptMeta> cache = new ConcurrentHashMap<>();
    private Consumer<PromptChangeEvent> changeListener;

    public NacosPromptSource(ConfigService configService, String dataId, String group) {
        this.configService = configService;
        this.dataId = dataId;
        this.group = group;

        // 1. 初始加载
        loadAll();

        // 2. 注册监听器
        registerNacosListener();
    }

    @Override
    public Map<String, PromptMeta> loadAll() {
        try {
            String config = configService.getConfig(dataId, group, 5000);

            if (config != null) {
                // 解析 YAML
                Map<String, PromptMeta> prompts = parseYamlConfig(config);
                cache.putAll(prompts);
                return prompts;
            }

        } catch (Exception e) {
            log.error("Failed to load config from Nacos", e);
        }

        return Collections.emptyMap();
    }

    @Override
    public void onChange(Consumer<PromptChangeEvent> listener) {
        this.changeListener = listener;
    }

    /**
     * 注册 Nacos 配置监听器
     */
    private void registerNacosListener() {
        try {
            configService.addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null; // 使用默认执行器
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("Received config update from Nacos");

                    // 解析新配置
                    Map<String, PromptMeta> newConfig = parseYamlConfig(configInfo);

                    // 计算差异
                    Set<String> updatedKeys = new HashSet<>();
                    Set<String> removedKeys = new HashSet<>();
                    Map<String, PromptMeta> updatedMetas = new HashMap<>();

                    for (Map.Entry<String, PromptMeta> entry : newConfig.entrySet()) {
                        String key = entry.getKey();
                        PromptMeta newMeta = entry.getValue();
                        PromptMeta oldMeta = cache.get(key);

                        if (oldMeta == null || !newMeta.getTemplate().equals(oldMeta.getTemplate())) {
                            updatedKeys.add(key);
                            updatedMetas.put(key, newMeta);
                        }
                    }

                    for (String key : cache.keySet()) {
                        if (!newConfig.containsKey(key)) {
                            removedKeys.add(key);
                        }
                    }

                    // 更新缓存
                    cache.clear();
                    cache.putAll(newConfig);

                    // 触发变更事件
                    if (changeListener != null &&
                        (!updatedKeys.isEmpty() || !removedKeys.isEmpty())) {

                        PromptChangeEvent event = new PromptChangeEvent(
                            updatedKeys, removedKeys, updatedMetas
                        );
                        changeListener.accept(event);
                    }
                }
            });

        } catch (Exception e) {
            log.error("Failed to register Nacos listener", e);
        }
    }

    /**
     * 解析 YAML 配置
     */
    private Map<String, PromptMeta> parseYamlConfig(String yaml) {
        // 使用 YAML 解析库(如 SnakeYAML)
        // 这里简化处理
        return new HashMap<>();
    }

    @Override
    public void close() throws Exception {
        // Nacos Client 会在 Spring 容器关闭时自动清理
    }
}
```

---

## 扩展 TemplateEngine

TemplateEngine 负责模板的编译和渲染,支持集成各种模板引擎。

### 接口定义

```java
public interface TemplateEngine {
    /**
     * 编译阶段:将字符串模板编译为可执行对象
     *
     * @param template 原始模板字符串
     * @param rootId 当前正在编译的模板 ID (用于循环引用检测)
     * @param partialLoader 子模板加载器 (输入子模板名称,返回子模板内容)
     * @return 编译后的对象
     */
    CompiledPrompt compile(String template, String rootId, Function<String, String> partialLoader);

    /**
     * 执行阶段:使用编译好的对象进行渲染
     *
     * @param compiledTemplate 编译后的对象 (来自于 compile 方法的返回值)
     * @param variables 变量上下文
     * @return 渲染后的字符串
     */
    String render(Object compiledTemplate, Map<String, Object> variables);
}
```

### 示例: 集成 FreeMarker

```java
package com.example.jprompt.extension;

import com.chih.JPrompt.core.spi.CompiledPrompt;
import com.chih.JPrompt.core.spi.TemplateEngine;
import freemarker.template.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * FreeMarker 模板引擎实现
 *
 * 依赖:
 * <dependency>
 *   <groupId>org.freemarker</groupId>
 *   <artifactId>freemarker</artifactId>
 *   <version>2.3.32</version>
 * </dependency>
 */
public class FreemarkerTemplateEngine implements TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(FreemarkerTemplateEngine.class);

    private final Configuration configuration;

    public FreemarkerTemplateEngine() {
        this.configuration = new Configuration(Configuration.VERSION_2_3_32);

        // 配置 FreeMarker
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setFallbackOnNullLoopVariable(false);
    }

    @Override
    public CompiledPrompt compile(String template, String rootId, Function<String, String> partialLoader) {
        try {
            // 创建 FreeMarker Template
            freemarker.template.Template fmTemplate =
                new Template(rootId, new StringReader(template), configuration);

            // 解析依赖关系
            Set<String> dependencies = parseDependencies(template, partialLoader);

            return new CompiledPrompt(fmTemplate, dependencies);

        } catch (IOException e) {
            throw new RuntimeException("Failed to compile FreeMarker template: " + rootId, e);
        }
    }

    @Override
    public String render(Object compiledTemplate, Map<String, Object> variables) {
        freemarker.template.Template template = (freemarker.template.Template) compiledTemplate;

        try (StringWriter writer = new StringWriter()) {
            // 创建数据模型
            TemplateModel model = createObjectWrapper().wrap(variables);

            // 渲染模板
            template.process(variables, writer);

            return writer.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to render FreeMarker template", e);
        }
    }

    /**
     * 解析模板依赖关系(支持 FreeMarker 的 <#include> 语法)
     */
    private Set<String> dependencies = new HashSet<>();

    private Set<String> parseDependencies(String template, Function<String, String> partialLoader) {
        // 简化实现:使用正则表达式匹配 <#include "filename">
        Pattern pattern = Pattern.compile("<#include\\s+\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(template);

        while (matcher.find()) {
            String partialName = matcher.group(1);
            dependencies.add(partialName);
        }

        return dependencies;
    }

    private ObjectWrapper createObjectWrapper() {
        return configuration.getObjectWrapper();
    }
}
```

**Spring Boot 集成**:

```java
@Configuration
public class JPromptExtensionConfiguration {

    @Bean
    @Primary
    public TemplateEngine freemarkerTemplateEngine() {
        return new FreemarkerTemplateEngine();
    }
}
```

---

## 扩展 PromptMetrics

PromptMetrics 负责记录监控指标,支持对接各种监控系统。

### 接口定义

```java
public interface PromptMetrics {
    /**
     * 记录一次 Prompt 渲染
     *
     * @param promptKey Prompt 的 ID
     * @param durationNs 耗时 (纳秒)
     * @param success 是否成功
     */
    void recordRender(String promptKey, long durationNs, boolean success);
}
```

### 示例: 集成 Prometheus Pushgateway

```java
package com.example.jprompt.extension;

import com.chih.JPrompt.core.spi.PromptMetrics;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.prometheus.client.push.PushGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Prometheus Pushgateway 监控指标实现
 *
 * 依赖:
 * <dependency>
 *   <groupId>io.prometheus</groupId>
 *   <artifactId>simpleclient_pushgateway</artifactId>
 *   <version>0.16.0</version>
 * </dependency>
 */
public class PrometheusPushGatewayMetrics implements PromptMetrics {

    private static final Logger log = LoggerFactory.getLogger(PrometheusPushGatewayMetrics.class);

    private final PushGateway pushGateway;
    private final String jobName;

    // 定义指标
    private final Counter renderCounter;
    private final Counter errorCounter;
    private final Histogram renderDuration;

    public PrometheusPushGatewayMetrics(String pushgatewayUrl, String jobName) {
        this.pushGateway = new PushGateway(pushgatewayUrl);
        this.jobName = jobName;

        // 注册指标
        CollectorRegistry registry = new CollectorRegistry();

        this.renderCounter = Counter.build()
            .name("jprompt_render_total")
            .help("Total number of prompt renders")
            .labelNames("prompt_key")
            .register(registry);

        this.errorCounter = Counter.build()
            .name("jprompt_render_errors_total")
            .help("Total number of prompt render errors")
            .labelNames("prompt_key")
            .register(registry);

        this.renderDuration = Histogram.build()
            .name("jprompt_render_duration_seconds")
            .help("Prompt render duration in seconds")
            .labelNames("prompt_key")
            .register(registry);
    }

    @Override
    public void recordRender(String promptKey, long durationNs, boolean success) {
        // 记录渲染次数
        renderCounter.labels(promptKey).inc();

        if (!success) {
            errorCounter.labels(promptKey).inc();
        }

        // 记录耗时(转换为秒)
        double durationSeconds = durationNs / 1_000_000_000.0;
        renderDuration.labels(promptKey).observe(durationSeconds);

        // 推送到 Pushgateway
        try {
            pushGateway.pushAdd(registry, jobName);
        } catch (IOException e) {
            log.error("Failed to push metrics to Prometheus Pushgateway", e);
        }
    }
}
```

**Spring Boot 集成**:

```java
@Configuration
public class JPromptExtensionConfiguration {

    @Bean
    @Primary
    public PromptMetrics prometheusMetrics(
            @Value("${jprompt.metrics.pushgateway.url}") String url,
            @Value("${jprompt.metrics.pushgateway.job}") String job) {
        return new PrometheusPushGatewayMetrics(url, job);
    }
}
```

**配置**:

```yaml
jprompt:
  metrics:
    pushgateway:
      url: http://localhost:9091
      job: jprompt-application
```

---

## 完整示例

### 自定义 PromptSource + TemplateEngine + Metrics

```java
// 1. 自定义 PromptSource - 从 Redis 加载
@Component
@Primary
public class RedisPromptSource implements PromptSource {
    // ... 实现略
}

// 2. 自定义 TemplateEngine - 使用 Thymeleaf
@Component
@Primary
public class ThymeleafTemplateEngine implements TemplateEngine {
    // ... 实现略
}

// 3. 自定义 Metrics - 发送到 Elasticsearch
@Component
@Primary
public class ElasticsearchMetrics implements PromptMetrics {
    // ... 实现略
}
```

---

## 最佳实践

### 1. 线程安全

所有 SPI 实现必须是线程安全的。

```java
// ✅ 使用线程安全集合
private final Map<String, PromptMeta> cache = new ConcurrentHashMap<>();

// ❌ 非线程安全
private final Map<String, PromptMeta> cache = new HashMap<>();
```

### 2. 资源管理

实现 `AutoCloseable` 接口,确保资源正确释放。

```java
@Override
public void close() throws Exception {
    if (executor != null) {
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }
    if (dataSource != null) {
        dataSource.close();
    }
}
```

### 3. 异常处理

捕获并记录异常,避免影响框架稳定性。

```java
@Override
public Map<String, PromptMeta> loadAll() {
    try {
        // 加载逻辑
    } catch (Exception e) {
        log.error("Failed to load prompts", e);
        return Collections.emptyMap(); // 返回空集合,不抛出异常
    }
}
```

### 4. 性能优化

使用缓存、连接池等技术提升性能。

```java
// 使用连接池
private final HikariDataSource dataSource;

// 使用缓存
private final LoadingCache<String, PromptMeta> cache = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build(this::loadFromDatabase);
```

### 5. 日志记录

使用适当的日志级别,记录关键操作。

```java
log.debug("Loading prompt: {}", key);  // 调试信息
log.info("Loaded {} prompts", count);  // 正常操作
log.warn("Prompt not found: {}", key); // 警告信息
log.error("Failed to load prompts", e); // 错误信息
```

---

## 相关资源

- [API 参考文档](api.md)
- [开发者指南](developer-guide.md)
- [配置参考手册](configuration.md)
- [项目主页](../README.md)
