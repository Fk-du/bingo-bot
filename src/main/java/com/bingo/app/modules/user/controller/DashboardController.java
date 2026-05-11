package com.bingo.app.modules.user.controller;

import com.bingo.app.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    @GetMapping
    public ResponseEntity<User> getDashboardData(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(user);
    }
}
