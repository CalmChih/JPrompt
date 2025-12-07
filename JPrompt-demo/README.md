# JPrompt Demo - 框架使用演示

## 项目简介

JPrompt Demo 是一个完整的 JPrompt 框架使用示例，展示了如何在实际项目中集成和使用 JPrompt 的核心功能。

## 核心特性演示

### 🎯 基础功能
- **简单模板渲染** - 基本的变量替换
- **复杂对象处理** - 嵌套对象访问和 Mustache 逻辑
- **代码审查** - Markdown 格式的专业代码分析
- **智能聊天** - 模板继承和上下文管理

### 🚀 高级特性
- **热更新支持** - 文件变更自动重载，无需重启
- **REST API 集成** - 完整的 HTTP 接口演示
- **监控集成** - Spring Boot Actuator 健康检查
- **批量处理** - 多个 Prompt 组合使用

## 快速开始

### 1. 环境要求
- Java 17+
- Maven 3.6+
- Spring Boot 3.x/4.x

### 2. 运行应用
```bash
# 克隆项目
git clone https://github.com/CalmChih/JPrompt.git
cd JPrompt

# 构建项目
mvn clean install

# 运行演示
mvn spring-boot:run -pl JPrompt-demo
```

### 3. 验证启动

启动成功后会看到以下输出：
```
====== JPrompt Demo Start ======

🔥 热更新功能已启用！
提示词文件路径: /your/path/JPrompt-demo/src/main/resources/prompts/prompts.yaml
修改提示词文件后，系统将在1秒内自动重载，无需重启应用！

[Test 1] Simple String Render:
输入: name = 'Developer'
输出: Hello Developer, welcome back!

[Test 2] Complex Object & Mustache Logic:
...

🌐 REST API 服务已启动！
访问地址: http://localhost:8080/api/prompts
```

## API 接口文档

### 基础接口

#### 1. 服务状态检查
```bash
GET /api/prompts/status
```

#### 2. 简单问候
```bash
GET /api/prompts/greet?name=YourName
```

#### 3. 订单分析
```bash
POST /api/prompts/analyze-order
Content-Type: application/json

{
  "id": "ORD-2025-9999",
  "totalPrice": 299.99,
  "items": ["Laptop", "Mouse", "Keyboard"],
  "user": {
    "name": "张三",
    "isVip": true
  }
}
```

#### 4. 代码审查
```bash
POST /api/prompts/review-code
Content-Type: application/json

{
  "code": "public static List cache = new ArrayList();"
}
```

#### 5. 智能聊天
```bash
POST /api/prompts/chat
Content-Type: application/json

{
  "assistantName": "JPrompt Assistant",
  "input": "请解释什么是内存泄漏？"
}
```

#### 6. 批量处理
```bash
POST /api/prompts/batch
Content-Type: application/json

{
  "name": "Alice",
  "order": {
    "id": "BATCH-001",
    "totalPrice": 199.99,
    "items": ["Book", "Pen"],
    "user": {
      "name": "Alice",
      "isVip": false
    }
  },
  "code": "for(int i=0; i<array.length; i++) { sum += array[i]; }",
  "message": "你好，请优化这段代码"
}
```

### 监控端点

#### 健康检查
```bash
GET /actuator/health
```
显示 JPrompt 服务状态和资源加载情况。

#### 应用信息
```bash
GET /actuator/info
```
显示应用基本信息。

#### 监控指标
```bash
GET /actuator/metrics
```
查看性能指标和缓存统计。

## 热更新演示

### 1. 找到提示词文件
```
JPrompt-demo/src/main/resources/prompts/prompts.yaml
```

### 2. 修改模板内容
```yaml
sayHello:
  template: "你好 {{name}}，欢迎使用 JPrompt 框架！"
```

### 3. 观察变化
无需重启应用，再次调用接口即可看到更新后的内容。

## 核心组件说明

### DemoMapper
```java
@PromptMapper
public interface DemoMapper {
    String sayHello(String name);

    @Prompt("order_analysis")
    String analyzeOrder(@Param("order") OrderDTO order);

    @Prompt("code_review")
    String reviewCode(@Param("code") String code);

    @Prompt("chat_with_header")
    String chat(@Param("assistantName") String botName, @Param("input") String msg);
}
```

### 提示词模板
```yaml
# 简单模板
sayHello:
  template: "Hello {{name}}, welcome back!"

# 复杂对象模板
order_analysis:
  model: gpt-4
  template: |
    Order ID: {{order.id}}
    User: {{order.user.name}}
    VIP: {{#order.user.isVip}}YES{{/order.user.isVip}}

# 模板继承
common_header:
  template: "System: You are {{assistantName}}."

chat_with_header:
  template: |
    {{> common_header}}
    User: {{input}}
```

## 配置说明

### application.yml
```yaml
jprompt:
  locations:
    - classpath:/prompts/
    - file:./custom-prompts/
  debounce-millis: 1000
  debug: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

## 测试建议

### 1. 功能测试
- 运行应用，观察控制台输出的 4 个测试场景
- 访问各个 REST API 接口
- 测试热更新功能

### 2. 性能测试
- 使用 `/actuator/metrics` 查看缓存命中率
- 观察并发请求下的响应时间
- 测试大对象模板渲染性能

### 3. 扩展测试
- 添加新的 DTO 和 Mapper 接口
- 创建自定义提示词模板
- 集成外部配置中心

## 最佳实践

1. **提示词管理**：将常用的提示词片段抽取为公共模板
2. **参数传递**：复杂对象建议使用 DTO 封装
3. **错误处理**：在生产环境中添加异常处理机制
4. **监控集成**：利用 Micrometer 收集使用指标
5. **安全考虑**：避免在模板中暴露敏感信息

## 故障排除

### 常见问题

1. **模板找不到**
   - 检查 `prompts.yaml` 文件路径
   - 确认 `jprompt.locations` 配置正确

2. **热更新不生效**
   - 确认文件监听权限
   - 检查 `debounce-millis` 配置

3. **API 返回 500 错误**
   - 查看 `/actuator/health` 健康状态
   - 检查应用日志中的错误信息

### 日志调试
在 `application.yml` 中开启调试模式：
```yaml
logging:
  level:
    com.chih.JPrompt: DEBUG
```

## 更多资源

- [JPrompt 核心文档](../CLAUDE.md)
- [JPrompt Starter 文档](../JPrompt-spring-boot-starter/CLAUDE.md)
- [官方 GitHub](https://github.com/CalmChih/JPrompt)
- [API 参考文档](../docs/api.md)

---

**JPrompt Team** - 让 AI 提示词管理变得简单高效！