package com.bingo.app.tenant.exception;

public class PlayerActionException extends RuntimeException {

    private final String userMessage;

    public PlayerActionException(String message, String userMessage) {
        super(message);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
