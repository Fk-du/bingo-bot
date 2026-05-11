package com.bingo.app.modules.user.controller;

import com.bingo.app.infrastructure.security.TelegramInitDataService;
import com.bingo.app.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TelegramInitDataService telegramInitDataService;

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestHeader("Authorization") String authorization) {
        User user = telegramInitDataService.resolveUser(authorization);
        return ResponseEntity.ok(user);
    }
}
