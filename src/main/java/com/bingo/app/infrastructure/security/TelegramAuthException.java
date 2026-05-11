package com.bingo.app.infrastructure.security;

public class TelegramAuthException extends RuntimeException {

    private final String userMessage;

    public TelegramAuthException(String message, String userMessage) {
        super(message);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
