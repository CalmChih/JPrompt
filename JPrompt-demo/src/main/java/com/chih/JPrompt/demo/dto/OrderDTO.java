package com.chih.JPrompt.demo.dto;

import java.util.List;

public class OrderDTO {
    private String id;
    private User user;
    private List<String> items;
    private double totalPrice;
    
    // 必须提供 Getters，Mustache 依赖 getter 访问属性
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items; }
    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }
    
    // 内部类 User
    public static class User {
        private String name;
        private boolean isVip;
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isVip() { return isVip; } // boolean getter
        public void setVip(boolean vip) { isVip = vip; }
    }
}