package com.bingo.app.config;

import com.bingo.app.modules.bot.core.TelegramBot;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class TelegramBotRegistrationConfig {

    private final TelegramBot telegramBot;

    @PostConstruct
    public void registerBot() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(telegramBot);
            log.info("Telegram long-polling registration completed for @{}", telegramBot.getBotUsername());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to register Telegram long-polling bot.", e);
        }
    }
}
