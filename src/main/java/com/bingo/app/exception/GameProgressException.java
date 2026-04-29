package com.bingo.app.exception;

public class GameProgressException extends RuntimeException {

    private final String userMessage;

    public GameProgressException(String message, String userMessage) {
        super(message);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
