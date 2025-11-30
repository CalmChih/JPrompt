package com.chih.JPrompt.core.exception;

public class PromptNotFoundException extends JPromptException {
    public PromptNotFoundException(String key) {
        super("Prompt not found for key: " + key);
    }
}