package com.bingo.app.infrastructure.security;

import org.springframework.security.core.AuthenticationException;

public class TelegramAuthException extends AuthenticationException {

    private static final long serialVersionUID = 1L;

    private final String userMessage;

    public TelegramAuthException(String message, String userMessage) {
        super(message);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
