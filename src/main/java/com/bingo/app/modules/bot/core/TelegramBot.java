package com.bingo.app.modules.bot.core;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook;

import com.bingo.app.modules.bot.handler.UpdateHandler;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {

    private final UpdateHandler updateHandler;

    @Value("${bingo.telegram.bot.username}")
    private String username;

    @Value("${bingo.telegram.bot.token}")
    private String token;

    @PostConstruct
    public void init() {
        log.info("TelegramBot bean initialized. username='{}'", username);
        try {
            var me = execute(new GetMe());
            log.info("Telegram API reachable. Bot id={}, username=@{}", me.getId(), me.getUserName());
            if (me.getUserName() == null || !me.getUserName().equalsIgnoreCase(username)) {
                throw new IllegalStateException(
                        "Configured bingo.telegram.bot.username='" + username
                                + "' does not match BOT_TOKEN owner username='"
                                + me.getUserName()
                                + "'. Update .env so BOT_USERNAME and BOT_TOKEN belong to the same bot."
                );
            }
            var deleteWebhookResult = execute(DeleteWebhook.builder().dropPendingUpdates(false).build());
            log.info("Webhook disabled for long polling mode: {}", deleteWebhookResult);
        } catch (Exception e) {
            log.error("Telegram API check failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Telegram bot validation failed during startup.", e);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        log.info(
                "Update received: hasMessage={}, hasCallback={}, messageText={}",
                update != null && update.hasMessage(),
                update != null && update.hasCallbackQuery(),
                update != null && update.hasMessage() ? update.getMessage().getText() : null
        );
        updateHandler.handle(update, this);
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }


}
