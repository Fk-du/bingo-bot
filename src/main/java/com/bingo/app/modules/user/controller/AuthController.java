package com.bingo.app.modules.user.controller;

import com.bingo.app.infrastructure.security.JwtUtils;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtils jwtUtils;

    @Data
    public static class AuthRequest {
        private String initData;
        private Long telegramId; // Temporarily direct for testing
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthRequest request) {
        // TODO: Validate initData with Telegram Bot Token

        User user = userService.findByTelegramId(request.getTelegramId())
                .orElseThrow(() -> new RuntimeException("User not found. Please register via Bot first."));

        String token = jwtUtils.generateToken(String.valueOf(user.getTelegramId()), new HashMap<>());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("user", user);

        return ResponseEntity.ok(response);
    }
}
