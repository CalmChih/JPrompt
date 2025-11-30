package com.chih.JPrompt.core.exception;

/**
 * JPrompt 框架根异常
 */
public class JPromptException extends RuntimeException {
    public JPromptException(String message) {
        super(message);
    }

    public JPromptException(String message, Throwable cause) {
        super(message, cause);
    }
}