package com.bingo.app.bot.handler;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.service.UserService;
import com.bingo.app.bot.callback.CallbackContext;
import com.bingo.app.bot.callback.CallbackRouter;
import com.bingo.app.bot.service.MenuService;
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

        User user = userService.findByTelegramId(telegramId);
        if (user == null) {
            log.warn("User not found for telegramId: {}", telegramId);
            sendMessage(bot, chatId, "User not found. Please use /start to register.");
            return;
        }

        CallbackContext ctx = CallbackContext.builder()
                .bot(bot)
                .chatId(chatId)
                .telegramId(telegramId)
                .user(user)
                .data(data)
                .build();

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

        // Default: tell user to use /start or the menu
        sendMessage(bot, chatId, "Welcome to BingoPlus! Use /start to get started, or open the app using the button below.");
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
