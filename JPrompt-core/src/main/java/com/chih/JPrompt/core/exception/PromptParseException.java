package com.chih.JPrompt.core.exception;

public class PromptParseException extends JPromptException {
    public PromptParseException(String fileName, Throwable cause) {
        super("Failed to parse prompt file: " + fileName, cause);
    }
}