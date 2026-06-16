package com.bingo.app.master.controller;

import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.master.dto.request.BroadcastRequest;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.service.UserService;
import com.bingo.app.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> broadcast(@Valid @RequestBody BroadcastRequest request) {
        List<User> recipients = resolveRecipients(request.target());

        int sent = 0;
        int failed = 0;

        for (User user : recipients) {
            try {
                bot.execute(SendMessage.builder()
                        .chatId(user.getTelegramId())
                        .text(request.message())
                        .build());
                sent++;
            } catch (TelegramApiException e) {
                log.warn("Broadcast failed for user {}: {}", user.getTelegramId(), e.getMessage());
                failed++;
            }
        }

        return ApiResponse.ok("Broadcast sent to " + sent + " user(s)" + (failed > 0 ? ", " + failed + " failed" : ""));
    }

    private List<User> resolveRecipients(String target) {
        return switch (target.toLowerCase()) {
            case "agents" -> userService.findAllByRole(Role.ADMIN);
            case "players" -> userService.findAllByRole(Role.PLAYER);
            default -> {
                List<User> all = new ArrayList<>(userService.findAllByRole(Role.ADMIN));
                all.addAll(userService.findAllByRole(Role.PLAYER));
                yield all;
            }
        };
    }
}
