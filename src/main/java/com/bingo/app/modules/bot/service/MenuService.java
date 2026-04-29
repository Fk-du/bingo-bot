package com.bingo.app.modules.bot.service;

import com.bingo.app.modules.bot.core.BotConstants;
import com.bingo.app.modules.bot.core.TelegramBot;
import com.bingo.app.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {

    @Value("${bingo.webapp.url}")
    private String webAppUrl;

    public void showMenu(TelegramBot bot, Update update, User user) {

        switch (user.getRole()) {
            case SUPER_ADMIN -> showSuperAdminMenu(bot, update);
            case ADMIN -> showAdminMenu(bot, update);
            case PLAYER -> showPlayerMenu(bot, update);
        }
    }

    private void showAdminMenu(TelegramBot bot, Update update) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(createWebAppBtn("Open Admin Panel", getWebAppUrl())));

        markup.setKeyboard(rows);
        send(bot, update, "Admin Menu\nClick the button below to manage your games and players:", markup);
    }

    private void showPlayerMenu(TelegramBot bot, Update update) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(createWebAppBtn("Play Bingo", getWebAppUrl())));

        markup.setKeyboard(rows);
        send(bot, update, "Player Menu\nClick the button below to join games and play:", markup);
    }

    private void showSuperAdminMenu(TelegramBot bot, Update update) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(createWebAppBtn("Super Admin Panel", getWebAppUrl())));

        markup.setKeyboard(rows);
        send(bot, update, "Super Admin Dashboard\nClick the button below to manage the entire system:", markup);
    }

    private InlineKeyboardButton createWebAppBtn(String text, String url) {
        InlineKeyboardButton btn = new InlineKeyboardButton(text);
        btn.setWebApp(new org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo(url));
        return btn;
    }

    private String getWebAppUrl() {
        return webAppUrl;
    }

    private InlineKeyboardButton createBtn(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton(text);
        btn.setCallbackData(callbackData);
        return btn;
    }

    private void send(TelegramBot bot, Update update, String msg, InlineKeyboardMarkup markup) {
        try {
            String chatId = resolveChatId(update);
            if (chatId == null) {
                log.warn("Cannot send menu message because chat id is missing in update");
                return;
            }

            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(msg)
                    .build();

            if (markup != null) {
                message.setReplyMarkup(markup);
            }

            bot.execute(message);
        } catch (Exception e) {
            log.error("Failed to send menu message: {}", e.getMessage(), e);
        }
    }

    private String resolveChatId(Update update) {
        if (update == null) {
            return null;
        }
        if (update.hasMessage() && update.getMessage().getChatId() != null) {
            return update.getMessage().getChatId().toString();
        }
        if (update.hasCallbackQuery()
                && update.getCallbackQuery().getMessage() != null
                && update.getCallbackQuery().getMessage().getChatId() != null) {
            return update.getCallbackQuery().getMessage().getChatId().toString();
        }
        return null;
    }
}
