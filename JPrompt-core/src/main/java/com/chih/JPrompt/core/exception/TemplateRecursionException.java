package com.chih.JPrompt.core.exception;

/**
 * 模板循环引用异常
 *
 * @author lizhiyuan
 */
public class TemplateRecursionException extends JPromptException {
    public TemplateRecursionException(String message) {
        super(message);
    }
}