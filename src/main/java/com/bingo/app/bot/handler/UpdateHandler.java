package com.bingo.app.bot.handler;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.service.UserService;
import com.bingo.app.bot.callback.CallbackContext;
import com.bingo.app.bot.callback.CallbackRouter;
import com.bingo.app.bot.service.InputStateService;
import com.bingo.app.infrastructure.persistence.TenantHelper;
import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.bot.command.StartCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateHandler {

    private final StartCommand startCommand;
    private final CallbackRouter callbackRouter;
    private final UserService userService;
    private final InputStateService inputStateService;
    private final PlayerBotHandler playerBotHandler;
    private final AdminBotHandler adminBotHandler;
    private final SuperAdminBotHandler superAdminBotHandler;

    public void handle(Update update, BingoTelegramBot bot) {
        if (update == null) {
            log.warn("Received null update");
            return;
        }

        // Handle callback queries (button presses)
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update, bot);
            return;
        }

        // Handle text messages
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update, bot);
            return;
        }
    }

    private void handleCallbackQuery(Update update, BingoTelegramBot bot) {
        String data = update.getCallbackQuery().getData();
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();

        log.debug("Callback query received: data={}, telegramId={}", data, telegramId);

        // Find user - returns User or null
        User user = userService.findByTelegramId(telegramId);
        if (user == null) {
            log.warn("User not found for telegramId: {}", telegramId);
            sendMessage(bot, chatId, "User not found. Please use /start to register.");
            return;
        }

        // Build callback context
        CallbackContext ctx = CallbackContext.builder()
                .bot(bot)
                .chatId(chatId)
                .telegramId(telegramId)
                .user(user)
                .data(data)
                .build();

        // Route to appropriate handler based on user role
        TenantHelper.runWithTenant(user, () -> callbackRouter.route(ctx));
    }

    private void handleTextMessage(Update update, BingoTelegramBot bot) {
        String text = update.getMessage().getText();
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        log.debug("Text message received: text={}, telegramId={}", text, telegramId);

        // Handle /start command
        if (text.startsWith("/start")) {
            startCommand.handle(update, bot);
            return;
        }

        // Check for pending input state (e.g., waiting for amount entry)
        InputStateService.PendingInput pending = inputStateService.getPendingInput(telegramId);
        if (pending != null) {
            handlePendingInput(update, bot, pending);
            return;
        }

        // Default: unknown command
        sendMessage(bot, chatId, "Unknown command. Please use the menu buttons or /start.");
    }

    private void handlePendingInput(Update update, BingoTelegramBot bot, InputStateService.PendingInput pending) {
        String text = update.getMessage().getText();
        Long telegramId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();

        // Clear pending action first
        inputStateService.clearPendingAction(telegramId);

        // Find user - returns User or null
        User user = userService.findByTelegramId(telegramId);
        if (user == null) {
            sendMessage(bot, chatId, "User not found. Please use /start.");
            return;
        }

        // Build context
        CallbackContext ctx = CallbackContext.builder()
                .bot(bot)
                .chatId(chatId)
                .telegramId(telegramId)
                .user(user)
                .data(text)  // Use the text input as data
                .build();

        // Route to appropriate handler based on role and pending action
        TenantHelper.runWithTenant(user, () -> {
            switch (user.getRole()) {
                case PLAYER:
                    playerBotHandler.handlePendingInput(ctx, pending);
                    break;
                case ADMIN:
                    adminBotHandler.handlePendingInput(ctx, pending);
                    break;
                case SUPER_ADMIN:
                    superAdminBotHandler.handlePendingInput(ctx, pending);
                    break;
                default:
                    sendMessage(bot, chatId, "Unknown role: " + user.getRole());
            }
        });
    }

    private void sendMessage(BingoTelegramBot bot, Long chatId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build();
            bot.execute(message);
        } catch (Exception e) {
            log.error("Failed to send message to {}: {}", chatId, e.getMessage());
        }
    }
}