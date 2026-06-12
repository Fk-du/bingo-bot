package com.bingo.app.bot.command;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.service.InviteService;
import com.bingo.app.master.service.UserService;
import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.bot.service.MenuService;
import com.bingo.app.infrastructure.persistence.TenantHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartCommand {

    private final UserService userService;
    private final InviteService inviteService;
    private final MenuService menuService;

    @Value("${app.super-admin.telegram-id}")
    private Long superAdminTelegramId;

    public void handle(Update update, BingoTelegramBot bot) {
        Long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText();
        String code = extractStartCode(text);
        Long chatId = update.getMessage().getChatId();

        log.info("Start command from telegramId={}, code={}", telegramId, code);

        // Check if user exists
        User existingUser = userService.findByTelegramId(telegramId);

        if (existingUser != null) {
            TenantHelper.runWithTenant(existingUser, () -> {
                sendMessage(bot, chatId, "👋 Welcome back! Use the button below to launch the app.");
                menuService.showMenu(bot, update, existingUser);
            });
            return;
        }

        // Super admin registration
        if (telegramId.equals(superAdminTelegramId)) {
            User superAdmin = userService.ensureSuperAdmin(telegramId);
            TenantHelper.runWithTenant(superAdmin, () -> {
                sendMessage(bot, chatId, "✅ Welcome Super Admin! You have full platform access. Use the button below to open the admin panel.");
                menuService.showMenu(bot, update, superAdmin);
            });
            return;
        }

        // New user with invite code
        if (code == null || code.isBlank()) {
            sendMessage(bot, chatId, "❌ Invalid invite link. Please use the link provided by your admin.");
            return;
        }

        try {
            User newUser = inviteService.registerWithInvite(telegramId, code);
            TenantHelper.runWithTenant(newUser, () -> {
                String roleText = newUser.getRole() == Role.ADMIN ? "Admin" : "Player";
                sendMessage(bot, chatId, "🎉 Welcome to BingoPlus! You have been registered as a " + roleText + ". Use the button below to launch the app.");
                menuService.showMenu(bot, update, newUser);
            });
        } catch (Exception e) {
            log.error("Registration failed for telegramId={}", telegramId, e);
            sendMessage(bot, chatId, "❌ Registration failed: " + e.getMessage());
        }
    }

    private String extractStartCode(String text) {
        String[] parts = text.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1].trim() : null;
    }

    private void sendMessage(BingoTelegramBot bot, Long chatId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build();
            bot.execute(message);
        } catch (Exception e) {
            log.error("Failed to send message: {}", e.getMessage());
        }
    }
}
