package com.chih.JPrompt.core.exception;

public class TemplateRenderException extends JPromptException {
    public TemplateRenderException(String key, Throwable cause) {
        super("Failed to render template for prompt: " + key, cause);
    }
}