package com.chih.JPrompt.demo;

import com.chih.JPrompt.demo.dto.OrderDTO;
import com.chih.JPrompt.demo.mapper.DemoMapper;
import com.chih.JPrompt.spring.annotation.PromptScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;

@SpringBootApplication
@PromptScan("com.chih.JPrompt.demo.mapper")
public class DemoApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
    
    @Bean
    public CommandLineRunner demoRunner(DemoMapper mapper) {
        return args -> {
            System.out.println("当前运行目录: " + System.getProperty("user.dir"));
            System.out.println("====== JPrompt Demo Start ======");

            // 热更新功能说明
            System.out.println("\n🔥 热更新功能已启用！");
            System.out.println("提示词文件路径: " +
                System.getProperty("user.dir") + "/JPrompt-demo/src/main/resources/prompts/prompts.yaml");
            System.out.println("修改提示词文件后，系统将在1秒内自动重载，无需重启应用！");
            System.out.println("可以尝试修改 prompts.yaml 中的模板内容，观察输出变化。");
            System.out.println();

            // --- 测试 1: 简单字符串渲染 ---
            // 演示最基本的模板变量替换功能
            // 对应 prompts.yaml 中的 sayHello 模板
            System.out.println("\n[Test 1] Simple String Render:");
            System.out.println("输入: name = 'Developer'");
            String simpleResult = mapper.sayHello("Developer");
            System.out.println("输出: " + simpleResult);
            
            
            // --- 测试 2: 复杂对象 & Mustache 逻辑渲染 ---
            // 演示复杂对象的深度访问和Mustache模板逻辑
            // 包含条件判断 {{#if}} 和循环 {{#each}} 功能
            System.out.println("\n[Test 2] Complex Object & Mustache Logic:");
            System.out.println("输入: 复杂订单对象，包含嵌套用户信息和商品列表");

            // 1. 构建复杂对象
            OrderDTO order = new OrderDTO();
            order.setId("ORD-2025-8888");
            order.setTotalPrice(199.99);
            order.setItems(Arrays.asList("Mechanical Keyboard", "Gaming Mouse", "USB-C Cable"));

            OrderDTO.User user = new OrderDTO.User();
            user.setName("Lizhiyuan");
            user.setVip(true); // 设置为 VIP，测试 {{#isVip}} 条件逻辑
            order.setUser(user);

            System.out.println("订单详情:");
            System.out.println("  - 订单号: " + order.getId());
            System.out.println("  - 用户: " + order.getUser().getName() + " (VIP: " + order.getUser().isVip() + ")");
            System.out.println("  - 商品数: " + order.getItems().size());

            // 2. 调用 Mapper（使用 @Prompt("order_analysis") 注解）
            // 注意：直接传递对象，Mustache会自动解析 order.user.name 等嵌套属性
            String complexResult = mapper.analyzeOrder(order);

            // 3. 打印结果
            System.out.println("\nAI分析结果:");
            System.out.println("---------------------------------------------");
            System.out.println(complexResult);
            System.out.println("---------------------------------------------");

            // 验证点：
            // 1. VIP状态应该显示为 "YES (High Priority)"
            // 2. 所有商品应该被循环列出
            // 3. 价格应该正确格式化显示
            
            // --- 测试 3: 代码审查功能 ---
            // 演示Markdown格式的代码审查功能
            // 对应 prompts.yaml 中的 code_review 模板，使用 GPT-4 模型
            System.out.println("\n[Test 3] Code Review (Markdown Format):");
            String codeSnippet = "public static Map cache = new HashMap();"; // 典型的内存泄漏代码
            System.out.println("输入代码: " + codeSnippet);
            System.out.println("审查重点: 内存泄漏、线程安全、性能优化");

            String reviewResult = mapper.reviewCode(codeSnippet);

            System.out.println("\nAI代码审查结果:");
            System.out.println("---------------------------------------------");
            System.out.println(reviewResult);
            System.out.println("---------------------------------------------");
            
            // --- 测试 4: 智能聊天功能（模板继承） ---
            // 演示模板继承功能，通过 {{> common_header}} 引用公共模板
            // 对应 prompts.yaml 中的 chat_with_header 模板
            System.out.println("\n[Test 4] Intelligent Chat (Template Inheritance):");
            System.out.println("输入: assistantName = 'JPrompt', input = 'Hi, who are you?'");
            System.out.println("功能: 使用模板继承，自动添加系统提示词");

            String botResponse = mapper.chat("JPrompt", "Hi, who are you?");

            System.out.println("\nAI回复:");
            System.out.println("---------------------------------------------");
            System.out.println(botResponse);
            System.out.println("---------------------------------------------");

            // API 访问说明
            System.out.println("\n🌐 REST API 服务已启动！");
            System.out.println("访问地址: http://localhost:8080/api/prompts");
            System.out.println("可用接口:");
            System.out.println("  GET  /api/prompts/status                    - 服务状态");
            System.out.println("  GET  /api/prompts/greet?name=YourName       - 问候接口");
            System.out.println("  POST /api/prompts/analyze-order             - 订单分析");
            System.out.println("  POST /api/prompts/review-code               - 代码审查");
            System.out.println("  POST /api/prompts/chat                      - 智能聊天");
            System.out.println("  POST /api/prompts/batch                     - 批量处理");
            System.out.println("\n监控端点:");
            System.out.println("  GET  /actuator/health                      - 健康检查");
            System.out.println("  GET  /actuator/info                        - 应用信息");
            System.out.println("  GET  /actuator/metrics                     - 监控指标");

            System.out.println("\n====== JPrompt Demo End ======");
        };
    }
}