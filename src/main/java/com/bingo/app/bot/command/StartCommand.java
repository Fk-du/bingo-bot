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

        // Check if user exists - returns User or null
        User existingUser = userService.findByTelegramId(telegramId);

        if (existingUser != null) {
            TenantHelper.runWithTenant(existingUser, () -> {
                sendWelcomeBackMessage(bot, chatId, existingUser);
                menuService.showMenu(bot, update, existingUser);
            });
            return;
        }

        // Super admin registration
        if (telegramId.equals(superAdminTelegramId)) {
            User superAdmin = userService.ensureSuperAdmin(telegramId);
            TenantHelper.runWithTenant(superAdmin, () -> {
                sendMessage(bot, chatId, "✅ Welcome Super Admin! You have full platform access.");
                menuService.showMenu(bot, update, superAdmin);
            });
            return;
        }

        // New user with invite code
        if (code == null || code.isBlank()) {
            sendMessage(bot, chatId, "❌ Invalid invite link. Please use the link provided by your agent.");
            return;
        }

        try {
            User newUser = inviteService.registerWithInvite(telegramId, code);
            TenantHelper.runWithTenant(newUser, () -> {
                sendWelcomeMessage(bot, chatId, newUser);
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

    private void sendWelcomeMessage(BingoTelegramBot bot, Long chatId, User user) {
        String roleText = user.getRole() == Role.ADMIN ? "Agent" : "Player";
        String message = String.format(
                "🎉 Welcome to BingoPlus, %s!\n\n" +
                        "You have been registered as a %s.\n\n" +
                        "Use the buttons below to get started.",
                user.getFirstName() != null ? user.getFirstName() : "User",
                roleText
        );
        sendMessage(bot, chatId, message);
    }

    private void sendWelcomeBackMessage(BingoTelegramBot bot, Long chatId, User user) {
        String message = String.format(
                "👋 Welcome back, %s!\n\n" +
                        "Your balance: 💰 %s\n\n" +
                        "What would you like to do?",
                user.getFirstName() != null ? user.getFirstName() : "User",
                user.getBalance()
        );
        sendMessage(bot, chatId, message);
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