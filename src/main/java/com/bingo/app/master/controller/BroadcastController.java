package com.bingo.app.master.controller;

import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.master.dto.request.BroadcastRequest;
import com.bingo.app.master.dto.response.AdminListItem;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.service.UserService;
import com.bingo.app.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/broadcast")
@RequiredArgsConstructor
@Slf4j
public class BroadcastController {

    private final BingoTelegramBot bot;
    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<String> broadcast(@Valid @RequestBody BroadcastRequest request,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        boolean isSuperAdmin = principal.getUser().getRole() == Role.SUPER_ADMIN;
        List<AdminListItem> recipients = resolveRecipients(request.target(), principal, isSuperAdmin);

        if (recipients.isEmpty()) {
            return ApiResponse.ok("No recipients found for target: " + request.target());
        }

        int sent = 0;
        int failed = 0;

        for (AdminListItem user : recipients) {
            try {
                bot.execute(SendMessage.builder()
                        .chatId(user.telegramId())
                        .text(request.message())
                        .build());
                sent++;
            } catch (TelegramApiException e) {
                log.warn("Broadcast failed for user {}: {}", user.telegramId(), e.getMessage());
                failed++;
            }
        }

        return ApiResponse.ok("Broadcast sent to " + sent + " user(s)" + (failed > 0 ? ", " + failed + " failed" : ""));
    }

    private List<AdminListItem> resolveRecipients(String target, UserPrincipal principal, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return switch (target.toLowerCase()) {
                case "agents" -> userService.findAllByRole(Role.ADMIN);
                case "players" -> userService.findAllByRole(Role.PLAYER);
                default -> {
                    List<AdminListItem> all = new ArrayList<>(userService.findAllByRole(Role.ADMIN));
                    all.addAll(userService.findAllByRole(Role.PLAYER));
                    yield all;
                }
            };
        }

        // Admin: can only broadcast to their own players
        Long adminUserId = principal.getUser().getId();
        return userService.findAllByAdminUserId(adminUserId);
    }
}
