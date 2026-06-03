package com.bingo.app.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.telegram.bot")
@Getter
@Setter
public class TelegramBotProperties {
    private String username;
    private String token;
}