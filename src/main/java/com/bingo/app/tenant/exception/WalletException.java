package com.bingo.app.tenant.exception;

public class WalletException extends RuntimeException {

    private final String userMessage;

    public WalletException(String message) {
        super(message);
        this.userMessage = message;
    }

    public WalletException(String message, String userMessage) {
        super(message);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
