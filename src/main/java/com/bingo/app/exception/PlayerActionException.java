package com.bingo.app.exception;

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
