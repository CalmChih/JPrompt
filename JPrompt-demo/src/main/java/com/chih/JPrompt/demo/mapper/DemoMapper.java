package com.chih.JPrompt.demo.mapper;

import com.chih.JPrompt.core.annotation.Param;
import com.chih.JPrompt.core.annotation.Prompt;
import com.chih.JPrompt.core.annotation.PromptMapper;
import com.chih.JPrompt.demo.dto.OrderDTO;

@PromptMapper
public interface DemoMapper {
    
    @Prompt("hello")
    String sayHello(@Param("name") String name);
    
    // 重点：参数是复杂对象
    @Prompt("order_analysis")
    String analyzeOrder(@Param("order") OrderDTO order);
    
    @Prompt("code_review")
    String reviewCode(@Param("code") String code);
}