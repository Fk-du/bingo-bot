package com.bingo.app.master.controller;

import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.master.entity.Notification;
import com.bingo.app.master.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<Notification>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(notificationService.getForUser(principal.getUser().getId(), Math.min(limit, 100)));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(Map.of("count", notificationService.countUnread(principal.getUser().getId())));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        notificationService.markRead(principal.getUser().getId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Long>> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(Map.of("count", notificationService.markAllRead(principal.getUser().getId())));
    }
}