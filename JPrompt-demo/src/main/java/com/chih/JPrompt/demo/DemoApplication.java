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
            System.out.println("====== JPrompt Demo Start ======");
            
            // --- 测试 1: 简单字符串渲染 ---
            System.out.println("\n[Test 1] Simple String Render:");
            String simpleResult = mapper.sayHello("Developer");
            System.out.println(simpleResult);
            
            
            // --- 测试 2: 复杂对象 & Mustache 逻辑渲染 ---
            System.out.println("\n[Test 2] Complex Object & Mustache Logic:");
            
            // 1. 构建复杂对象
            OrderDTO order = new OrderDTO();
            order.setId("ORD-2025-8888");
            order.setTotalPrice(199.99);
            order.setItems(Arrays.asList("Mechanical Keyboard", "Gaming Mouse", "USB-C Cable"));
            
            OrderDTO.User user = new OrderDTO.User();
            user.setName("Lizhiyuan");
            user.setVip(true); // 设置为 VIP，测试 {{#isVip}} 逻辑
            order.setUser(user);
            
            // 2. 调用 Mapper
            // 注意：我们直接把对象传进去，Mustache 会自动解析 order.user.name 等
            String complexResult = mapper.analyzeOrder(order);
            
            // 3. 打印结果
            System.out.println("---------------------------------------------");
            System.out.println(complexResult);
            System.out.println("---------------------------------------------");
            
            // 验证点：
            // 1. 检查 VIP 是否显示为 "YES (High Priority)"
            // 2. 检查 Items 是否被循环打印出来了
            
            System.out.println("====== JPrompt Demo End ======");
            
            System.out.println("\n[Test 3] Markdown Format Test:");
            String codeSnippet = "public static Map cache = new HashMap();"; // 典型的内存泄漏代码
            String reviewResult = mapper.reviewCode(codeSnippet);
            
            System.out.println("---------------------------------------------");
            System.out.println(reviewResult);
            System.out.println("---------------------------------------------");
        };
    }
}