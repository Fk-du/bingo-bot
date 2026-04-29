//package com.bingo.app.modules.user.controller;
//
//import com.bingo.app.modules.user.entity.User;
//import com.bingo.app.modules.user.service.UserService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.core.annotation.AuthenticationPrincipal;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.math.BigDecimal;
//import java.util.HashMap;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/v1/dashboard")
//@RequiredArgsConstructor
//public class DashboardController {
//
//    private final UserService userService;
//
//    @GetMapping
//    public ResponseEntity<Map<String, Object>> getDashboardData(@AuthenticationPrincipal User user) {
//        Map<String, Object> data = new HashMap<>();
//        data.put("telegramId", user.getTelegramId());
//        data.put("role", user.getRole());
//        data.put("balance", user.getBalance());
//        data.put("active", user.isActive());
//
//        // You could add more aggregate data here (e.g. total games played)
//
//        return ResponseEntity.ok(data);
//    }
//}
