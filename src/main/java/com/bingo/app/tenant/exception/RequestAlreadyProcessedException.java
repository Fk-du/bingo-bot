package com.bingo.app.tenant.exception;

public class RequestAlreadyProcessedException extends WalletException {

    public RequestAlreadyProcessedException(String message) {
        super(message, "This request has already been processed.");
    }
}
