package com.bingo.app.bot.service;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.bot.BotConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
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

    public void showMenu(BingoTelegramBot bot, Update update, User user) {
        Long chatId = update.getMessage() != null ?
                update.getMessage().getChatId() :
                update.getCallbackQuery().getMessage().getChatId();

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        switch (user.getRole()) {
            case PLAYER -> {
                message.setText("🎮 Welcome to BingoPlus!\n\nUse the Launch App button to access the game.");
                keyboard.add(createRow(createWebAppButton("🚀 Launch App")));
            }
            case ADMIN -> {
                message.setText("🎯 Admin Dashboard\n\nManage your games in the app or generate an invite link for players.");
                keyboard.add(createRow(createButton("🔗 Invite Link", BotConstants.INVITE_LINK)));
                keyboard.add(createRow(createWebAppButton("🚀 Launch App")));
            }
            case SUPER_ADMIN -> {
                message.setText("👑 Super Admin Panel\n\nManage your platform in the app or generate an admin invite link.");
                keyboard.add(createRow(createButton("🚀 Create Admin", BotConstants.CREATE_ADMIN)));
                keyboard.add(createRow(createWebAppButton("🚀 Launch App")));
            }
        }

        markup.setKeyboard(keyboard);
        message.setReplyMarkup(markup);
        message.setParseMode("Markdown");

        try {
            bot.execute(message);
        } catch (Exception e) {
            log.error("Failed to send menu: {}", e.getMessage());
        }
    }

    private List<InlineKeyboardButton> createRow(InlineKeyboardButton... buttons) {
        return List.of(buttons);
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private InlineKeyboardButton createWebAppButton(String text) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setWebApp(new WebAppInfo(webAppUrl));
        return button;
    }
}
