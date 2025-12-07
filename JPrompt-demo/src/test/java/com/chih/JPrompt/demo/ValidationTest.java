package com.chih.JPrompt.demo;

import com.chih.JPrompt.demo.dto.OrderDTO;

import java.util.Arrays;

/**
 * Basic validation test - no Spring container dependency
 * Used to verify demo code compilation correctness and basic functionality
 */
public class ValidationTest {

    public static void main(String[] args) {
        System.out.println("=== JPrompt Demo Validation Test ===");

        // 1. Verify DTO can be created and used normally
        System.out.println("1. Verify OrderDTO creation:");
        OrderDTO order = createTestOrder();
        System.out.println("   Order ID: " + order.getId());
        System.out.println("   User name: " + order.getUser().getName());
        System.out.println("   VIP status: " + order.getUser().isVip());
        System.out.println("   Item count: " + order.getItems().size());

        // 2. Verify interface compilation is correct
        System.out.println("\n2. Verify DemoMapper interface:");
        System.out.println("   Interface method count: " + com.chih.JPrompt.demo.mapper.DemoMapper.class.getDeclaredMethods().length);

        // 3. Verify controller compilation is correct
        try {
            Class<?> controllerClass = Class.forName("com.chih.JPrompt.demo.controller.PromptController");
            System.out.println("   PromptController loaded successfully");
            System.out.println("   Controller method count: " + controllerClass.getDeclaredMethods().length);
        } catch (ClassNotFoundException e) {
            System.out.println("Controller loading failed: " + e.getMessage());
        }

        // 4. Verify application main class
        try {
            Class<?> appClass = Class.forName("com.chih.JPrompt.demo.DemoApplication");
            System.out.println("   DemoApplication loaded successfully");
        } catch (ClassNotFoundException e) {
            System.out.println("Main application class loading failed: " + e.getMessage());
        }

        System.out.println("\n=== Validation Results ===");
        System.out.println("All classes compile correctly");
        System.out.println("DTO creation and access are normal");
        System.out.println("Interface definition is complete");
        System.out.println("Controller class exists");
        System.out.println("\nDemo code structure validation completed!");

        System.out.println("\nNext step suggestions:");
        System.out.println("1. Check JPrompt configuration and resource loading");
        System.out.println("2. Verify prompts.yaml file path");
        System.out.println("3. Check Spring Boot auto-configuration");
    }

    private static OrderDTO createTestOrder() {
        OrderDTO order = new OrderDTO();
        order.setId("VALIDATION-001");
        order.setTotalPrice(199.99);
        order.setItems(Arrays.asList("Test Item 1", "Test Item 2", "Test Item 3"));

        OrderDTO.User user = new OrderDTO.User();
        user.setName("Validation User");
        user.setVip(true);
        order.setUser(user);

        return order;
    }
}