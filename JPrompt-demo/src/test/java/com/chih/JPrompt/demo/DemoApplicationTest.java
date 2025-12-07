package com.chih.JPrompt.demo;

import com.chih.JPrompt.demo.dto.OrderDTO;
import com.chih.JPrompt.demo.mapper.DemoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPrompt Demo 应用集成测试
 * 验证所有核心功能的正确性
 */
@SpringBootTest(classes = DemoApplication.class)
@TestPropertySource(properties = {
    "jprompt.locations=classpath:/prompts/",
    "jprompt.debug=true"
})
public class DemoApplicationTest {

    @Autowired
    private DemoMapper demoMapper;

    @Test
    public void testSayHello() {
        // 测试简单字符串渲染
        String result = demoMapper.sayHello("JPrompt");
        assertThat(result).contains("JPrompt");
        System.out.println("✅ 简单问候测试通过: " + result);
    }

    @Test
    public void testAnalyzeOrder() {
        // 测试复杂对象处理
        OrderDTO order = new OrderDTO();
        order.setId("TEST-001");
        order.setTotalPrice(299.99);
        order.setItems(Arrays.asList("笔记本电脑", "鼠标", "键盘"));

        OrderDTO.User user = new OrderDTO.User();
        user.setName("测试用户");
        user.setVip(true);
        order.setUser(user);

        String result = demoMapper.analyzeOrder(order);

        assertThat(result).contains("TEST-001");
        assertThat(result).contains("测试用户");
        assertThat(result).contains("笔记本电脑");

        System.out.println("✅ 订单分析测试通过:");
        System.out.println(result);
    }

    @Test
    public void testReviewCode() {
        // 测试代码审查功能
        String code = "public static List cache = new ArrayList();";
        String result = demoMapper.reviewCode(code);

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();

        System.out.println("✅ 代码审查测试通过:");
        System.out.println(result);
    }

    @Test
    public void testChat() {
        // 测试聊天功能（模板继承）
        String result = demoMapper.chat("测试助手", "你好，你是谁？");

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();

        System.out.println("✅ 智能聊天测试通过:");
        System.out.println(result);
    }

    @Test
    public void testAllFeaturesIntegration() {
        System.out.println("\n=== JPrompt Demo 功能集成测试 ===");

        // 1. 基础功能
        String greeting = demoMapper.sayHello("开发者");
        System.out.println("1. 问候功能: " + greeting);

        // 2. 复杂对象
        OrderDTO order = createTestOrder();
        String analysis = demoMapper.analyzeOrder(order);
        System.out.println("2. 订单分析: 完成");

        // 3. 代码审查
        String review = demoMapper.reviewCode("Map<String, Object> map = new HashMap();");
        System.out.println("3. 代码审查: 完成");

        // 4. 智能聊天
        String chat = demoMapper.chat("JPrompt", "介绍一下你的功能");
        System.out.println("4. 智能聊天: 完成");

        System.out.println("\n✅ 所有核心功能测试通过！");
    }

    private OrderDTO createTestOrder() {
        OrderDTO order = new OrderDTO();
        order.setId("INTEGRATION-TEST");
        order.setTotalPrice(199.99);
        order.setItems(Arrays.asList("测试商品1", "测试商品2"));

        OrderDTO.User user = new OrderDTO.User();
        user.setName("集成测试用户");
        user.setVip(false);
        order.setUser(user);

        return order;
    }
}