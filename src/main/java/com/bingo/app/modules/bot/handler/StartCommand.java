package com.bingo.app.modules.bot.handler;

import com.bingo.app.infrastructure.tenant.TenantContext;
import com.bingo.app.infrastructure.tenant.TenantHelper;
import com.bingo.app.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.bingo.app.modules.bot.core.TelegramBot;
import com.bingo.app.exception.InviteRegistrationException;
import com.bingo.app.modules.invite.service.InviteService;
import com.bingo.app.modules.bot.service.MenuService;
import com.bingo.app.modules.user.service.UserService;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartCommand {

    private final UserService userService;
    private final InviteService inviteService;
    private final MenuService menuService;

    @Value("${app.super-admin.telegram-id}")
    private Long superAdminId;

    public void handle(Update update, TelegramBot bot) {

        Long telegramId = update.getMessage().getFrom().getId();
        String code = extractStartCode(update.getMessage().getText());
        log.info("StartCommand invoked for telegramId={}", telegramId);

        if (telegramId.equals(superAdminId)) {
            User superAdmin = userService.ensureSuperAdmin(telegramId);
            TenantHelper.runWithTenant(superAdmin, () -> {
                send(bot, update, "Super admin access confirmed.");
                menuService.showMenu(bot, update, superAdmin);
            });
            return;
        }

        var userOpt = userService.findByTelegramId(telegramId);

        if (userOpt.isPresent()) {
            User existing = userOpt.get();
            TenantHelper.runWithTenant(existing, () -> menuService.showMenu(bot, update, existing));
            return;
        }

        if (code == null || code.isBlank()) {
            send(bot, update, "You must join with a valid invite link from your admin.");
            return;
        }

        try {
            User user = inviteService.registerWithInvite(telegramId, code);
            TenantHelper.runWithTenant(user, () -> {
                send(bot, update, "Registration completed successfully.");
                menuService.showMenu(bot, update, user);
            });
        } catch (InviteRegistrationException ex) {
            log.warn("Invite registration failed for telegramId={}: {}", telegramId, ex.getMessage());
            send(bot, update, ex.getUserMessage());
        }
    }

    private String extractStartCode(String text) {
        String[] parts = text.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1].trim() : null;
    }

    private void send(TelegramBot bot, Update update, String msg) {
        try {
            bot.execute(
                org.telegram.telegrambots.meta.api.methods.send.SendMessage
                        .builder()
                        .chatId(update.getMessage().getChatId().toString())
                        .text(msg)
                        .build()
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
