package com.bingo.app.modules.bot.handler;

import com.bingo.app.modules.bot.context.CallbackContext;
import com.bingo.app.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.bingo.app.modules.bot.core.TelegramBot;


@RequiredArgsConstructor
@Component
@Slf4j
public class UpdateHandler {

    private final StartCommand startCommand;
    private final CallbackRouter router;
    private final UserService userService;

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

            router.route(ctx);
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            if (update.getMessage().getText().startsWith("/start")) {
                startCommand.handle(update, bot);
            }
        }
    }
}