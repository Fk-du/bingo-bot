package com.bingo.app.modules.bot.context;

import com.bingo.app.modules.bot.core.TelegramBot;
import com.bingo.app.modules.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CallbackContext {
    private TelegramBot bot;
    private Long chatId;
    private Long telegramId;
    private User user;
    private String data;
}
