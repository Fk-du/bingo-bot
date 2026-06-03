package com.bingo.app.common.exception;

import com.bingo.app.master.exception.InviteRegistrationException;
import com.bingo.app.tenant.exception.GameCreationException;
import com.bingo.app.tenant.exception.GameProgressException;
import com.bingo.app.tenant.exception.PlayerActionException;
import com.bingo.app.infrastructure.security.TelegramAuthException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({
            GameCreationException.class,
            GameProgressException.class,
            InviteRegistrationException.class,
            PlayerActionException.class
    })
    public ResponseEntity<Map<String, Object>> handleDomainException(RuntimeException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), userMessage(ex));
    }

    @ExceptionHandler(TelegramAuthException.class)
    public ResponseEntity<Map<String, Object>> handleTelegramAuthException(TelegramAuthException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex.getUserMessage());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message, String userMessage) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", message);
        body.put("userMessage", userMessage);
        body.put("status", status.value());
        return ResponseEntity.status(status).body(body);
    }

    private String userMessage(RuntimeException ex) {
        if (ex instanceof GameCreationException gameCreationException) {
            return gameCreationException.getUserMessage();
        }
        if (ex instanceof GameProgressException gameProgressException) {
            return gameProgressException.getUserMessage();
        }
        if (ex instanceof InviteRegistrationException inviteRegistrationException) {
            return inviteRegistrationException.getUserMessage();
        }
        if (ex instanceof PlayerActionException playerActionException) {
            return playerActionException.getUserMessage();
        }
        return ex.getMessage();
    }
}
