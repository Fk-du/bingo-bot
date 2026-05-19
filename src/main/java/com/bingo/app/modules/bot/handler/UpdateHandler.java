package com.bingo.app.modules.bot.handler;

import com.bingo.app.infrastructure.tenant.TenantHelper;
import com.bingo.app.modules.bot.context.CallbackContext;
import com.bingo.app.modules.bot.service.InputStateService;
import com.bingo.app.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.bingo.app.modules.bot.core.TelegramBot;
import com.bingo.app.modules.bot.handler.impl.PlayerBotHandler;
import com.bingo.app.modules.bot.handler.impl.AdminBotHandler;
import com.bingo.app.modules.bot.handler.impl.SuperAdminBotHandler;


@RequiredArgsConstructor
@Component
@Slf4j
public class UpdateHandler {

    private final StartCommand startCommand;
    private final CallbackRouter router;
    private final UserService userService;
    private final InputStateService inputStateService;
    private final PlayerBotHandler playerBotHandler;
    private final AdminBotHandler adminBotHandler;
    private final SuperAdminBotHandler superAdminBotHandler;

    public void handle(Update update, TelegramBot bot) {

        if (update.hasCallbackQuery()) {

            String data = update.getCallbackQuery().getData();
            Long telegramId = update.getCallbackQuery().getFrom().getId();
            Long chatId = update.getCallbackQuery().getMessage().getChatId();

            var user = userService.findByTelegramId(telegramId)
                    .orElseThrow();

            var ctx = CallbackContext.builder()
                    .bot(bot)
                    .chatId(chatId)
                    .telegramId(telegramId)
                    .user(user)
                    .data(data)
                    .build();

            TenantHelper.runWithTenant(user, () -> router.route(ctx));
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            Long telegramId = update.getMessage().getFrom().getId();
            Long chatId = update.getMessage().getChatId();

            if (text.startsWith("/start")) {
                startCommand.handle(update, bot);
                return;
            }

            // Handle pending input state
            InputStateService.PendingInput pending = inputStateService.getPendingInput(telegramId);
            if (pending != null) {
                inputStateService.clearPendingAction(telegramId);
                var userOpt = userService.findByTelegramId(telegramId);
                if (userOpt.isEmpty()) return;

                var user = userOpt.get();
                var ctx = CallbackContext.builder()
                        .bot(bot)
                        .chatId(chatId)
                        .telegramId(telegramId)
                        .user(user)
                        .data(text)
                        .build();

                TenantHelper.runWithTenant(user, () -> {
                    switch (user.getRole()) {
                        case PLAYER -> playerBotHandler.handlePendingInput(ctx, pending);
                        case ADMIN -> adminBotHandler.handlePendingInput(ctx, pending);
                        case SUPER_ADMIN -> superAdminBotHandler.handlePendingInput(ctx, pending);
                    }
                });
                return;
            }
        }
    }
}