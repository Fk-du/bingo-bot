package com.bingo.app.master.controller;

import com.bingo.app.infrastructure.security.TelegramAuthService;
import com.bingo.app.master.dto.mapper.MasterMapper;
import com.bingo.app.master.dto.request.LoginRequest;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TelegramAuthService telegramAuthService;
    private final MasterMapper masterMapper;

    @PostMapping("/login")
    public ApiResponse<UserProfileResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = telegramAuthService.authenticate(request.initData());
        if (user == null) {
            return ApiResponse.error("Authentication failed");
        }
        return ApiResponse.ok("Authenticated", masterMapper.toUserProfile(user));
    }
}
