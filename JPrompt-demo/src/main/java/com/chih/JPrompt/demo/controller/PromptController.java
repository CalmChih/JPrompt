package com.chih.JPrompt.demo.controller;

import com.chih.JPrompt.demo.dto.OrderDTO;
import com.chih.JPrompt.demo.mapper.DemoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * JPrompt Web API 演示控制器
 * <p>
 * 展示如何在Spring Boot应用中通过HTTP接口使用JPrompt功能。
 * 包含所有核心功能的REST API示例。
 * </p>
 *
 * @author JPrompt Team
 * @since 2025/12/08
 */
@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
@Slf4j
public class PromptController {

    private final DemoMapper demoMapper;

    /**
     * 简单问候接口
     *
     * @param name 用户姓名
     * @return 问候消息
     */
    @GetMapping("/greet")
    public ResponseEntity<Map<String, Object>> greet(@RequestParam(defaultValue = "World") String name) {
        log.info("收到问候请求，用户：{}", name);

        String result = demoMapper.sayHello(name);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
            "input", Map.of("name", name),
            "output", result,
            "template", "sayHello"
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * 订单分析接口
     *
     * @param orderData 订单数据JSON
     * @return 分析结果
     */
    @PostMapping("/analyze-order")
    public ResponseEntity<Map<String, Object>> analyzeOrder(@RequestBody Map<String, Object> orderData) {
        log.info("收到订单分析请求：{}", orderData);

        // 转换为OrderDTO对象
        OrderDTO order = convertToOrderDTO(orderData);
        String result = demoMapper.analyzeOrder(order);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
            "input", orderData,
            "output", result,
            "template", "order_analysis",
            "model", "gpt-4"
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * 代码审查接口
     *
     * @param codeRequest 包含代码片段的请求
     * @return 审查结果
     */
    @PostMapping("/review-code")
    public ResponseEntity<Map<String, Object>> reviewCode(@RequestBody Map<String, String> codeRequest) {
        String code = codeRequest.get("code");
        log.info("收到代码审查请求，代码长度：{}", code != null ? code.length() : 0);

        String result = demoMapper.reviewCode(code);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
            "input", Map.of("code", code),
            "output", result,
            "template", "code_review",
            "model", "gpt-4"
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * 智能聊天接口
     *
     * @param chatRequest 聊天请求
     * @return 聊天回复
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> chatRequest) {
        String botName = chatRequest.getOrDefault("assistantName", "JPrompt Assistant");
        String message = chatRequest.get("input");

        log.info("收到聊天请求，助手：{}，消息：{}", botName, message);

        String result = demoMapper.chat(botName, message);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", Map.of(
            "input", Map.of("assistantName", botName, "input", message),
            "output", result,
            "template", "chat_with_header",
            "model", "gpt-3.5"
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * 批量处理接口 - 演示多个Prompt的组合使用
     *
     * @param request 批量处理请求
     * @return 批量处理结果
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batchProcess(@RequestBody Map<String, Object> request) {
        log.info("收到批量处理请求");

        Map<String, Object> results = new HashMap<>();

        try {
            // 1. 问候
            String name = (String) request.getOrDefault("name", "User");
            results.put("greeting", demoMapper.sayHello(name));

            // 2. 订单分析（如果提供订单数据）
            if (request.containsKey("order")) {
                OrderDTO order = convertToOrderDTO((Map<String, Object>) request.get("order"));
                results.put("orderAnalysis", demoMapper.analyzeOrder(order));
            }

            // 3. 代码审查（如果提供代码）
            if (request.containsKey("code")) {
                String code = (String) request.get("code");
                results.put("codeReview", demoMapper.reviewCode(code));
            }

            // 4. 聊天（如果提供消息）
            if (request.containsKey("message")) {
                String message = (String) request.get("message");
                results.put("chatResponse", demoMapper.chat("JPrompt", message));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", Map.of(
                "results", results,
                "processedCount", results.size()
            ));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("批量处理失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 健康检查和状态信息接口
     *
     * @return JPrompt服务状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> status = new HashMap<>();
        status.put("service", "JPrompt Demo");
        status.put("status", "running");
        status.put("features", Arrays.asList(
            "Simple Template Rendering",
            "Complex Object Processing",
            "Code Review Analysis",
            "Intelligent Chat",
            "Batch Processing",
            "Hot Reload Support"
        ));
        status.put("endpoints", Arrays.asList(
            "GET  /api/prompts/greet",
            "POST /api/prompts/analyze-order",
            "POST /api/prompts/review-code",
            "POST /api/prompts/chat",
            "POST /api/prompts/batch",
            "GET  /api/prompts/status"
        ));

        return ResponseEntity.ok(status);
    }

    /**
     * 将Map转换为OrderDTO对象
     */
    private OrderDTO convertToOrderDTO(Map<String, Object> orderData) {
        OrderDTO order = new OrderDTO();

        if (orderData.containsKey("id")) {
            order.setId((String) orderData.get("id"));
        }
        if (orderData.containsKey("totalPrice")) {
            order.setTotalPrice(((Number) orderData.get("totalPrice")).doubleValue());
        }
        if (orderData.containsKey("items")) {
            @SuppressWarnings("unchecked")
            java.util.List<String> items = (java.util.List<String>) orderData.get("items");
            order.setItems(items);
        }

        // 处理用户信息
        if (orderData.containsKey("user")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> userData = (Map<String, Object>) orderData.get("user");
            OrderDTO.User user = new OrderDTO.User();

            if (userData.containsKey("name")) {
                user.setName((String) userData.get("name"));
            }
            if (userData.containsKey("isVip")) {
                user.setVip((Boolean) userData.get("isVip"));
            }

            order.setUser(user);
        }

        return order;
    }
}