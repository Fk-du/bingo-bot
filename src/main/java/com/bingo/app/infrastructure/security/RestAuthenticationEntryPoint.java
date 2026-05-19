package com.bingo.app.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        writeError(response, HttpStatus.UNAUTHORIZED, authException.getMessage(), userMessage(authException));
    }

    private String userMessage(AuthenticationException authException) {
        if (authException instanceof TelegramAuthException telegramAuthException) {
            return telegramAuthException.getUserMessage();
        }
        return "Authentication is required to access this resource.";
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message, String userMessage)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("userMessage", userMessage);
        body.put("status", status.value());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
