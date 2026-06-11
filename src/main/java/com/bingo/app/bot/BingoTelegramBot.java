package com.bingo.app.bot;

import com.bingo.app.bot.handler.UpdateHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("deprecation")
public class BingoTelegramBot extends TelegramLongPollingBot {

    private final UpdateHandler updateHandler;

    @Value("${app.telegram.bot.username}")
    private String username;

    @Value("${app.telegram.bot.token}")
    private String token;

    private Long botId;

    @PostConstruct
    public void init() {
        log.info("Initializing Telegram Bot: @{}", username);
        log.info("Bot token configured: {}", token != null ? "YES (length: " + token.length() + ")" : "NO");

        if (token == null || token.isEmpty() || token.equals("your_bot_token")) {
            log.error("BOT_TOKEN is not properly configured! Please check your ..env file");
            throw new IllegalStateException("Telegram bot token is not configured");
        }

        try {
            var me = execute(new GetMe());
            botId = me.getId();
            log.info("Bot connected: id={}, username=@{}", me.getId(), me.getUserName());
        } catch (TelegramApiException e) {
            log.error("Failed to connect bot: {}", e.getMessage());
            log.error("Please verify your BOT_TOKEN is correct");
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
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

    public Long getBotId() {
        return botId;
    }
}
