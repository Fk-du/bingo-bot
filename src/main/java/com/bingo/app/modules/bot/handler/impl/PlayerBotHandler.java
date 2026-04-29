package com.bingo.app.modules.bot.handler.impl;

import com.bingo.app.modules.bot.context.CallbackContext;
import com.bingo.app.modules.bot.core.BotConstants;
import com.bingo.app.modules.bot.core.TelegramBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlayerBotHandler {

    public void handle(CallbackContext ctx) {
        String data = ctx.getData();
        log.info("PlayerBotHandler handling: {}", data);

        if (data.equals(BotConstants.BALANCE)) {
            handleBalance(ctx);
        } else {
            send(ctx.getBot(), ctx.getChatId(), "This feature is not yet implemented. Use the Mini App for rich gameplay!");
        }
    }

    private void handleBalance(CallbackContext ctx) {
        String msg = "💰 *Your Balance*\n\n" +
                     "Current Points: `" + ctx.getUser().getBalance() + "`\n\n" +
                     "You can top up via your agent or join games using your points.";
        sendMarkdown(ctx.getBot(), ctx.getChatId(), msg);
    }

    private void send(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build());
        } catch (Exception e) {
            log.error("Failed to send message", e);
        }
    }

    private void sendMarkdown(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .build());
        } catch (Exception e) {
            log.error("Failed to send markdown message", e);
        }
    }
}
