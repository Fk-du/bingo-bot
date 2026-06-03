package com.bingo.app.bot.callback;

import com.bingo.app.master.entity.User;
import com.bingo.app.bot.BingoTelegramBot;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CallbackContext {
    private BingoTelegramBot bot;
    private Long chatId;
    private Long telegramId;
    private User user;
    private String data;
    private Integer messageId;

    public boolean hasData() {
        return data != null && !data.isEmpty();
    }

    public Long getUserId() {
        return user != null ? user.getId() : null;
    }
}