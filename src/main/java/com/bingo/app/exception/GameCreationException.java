package com.bingo.app.exception;

public class GameCreationException extends RuntimeException {

    private final String userMessage;

    public GameCreationException(String message, String userMessage) {
        super(message);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
