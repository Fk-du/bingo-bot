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
            case PLAYER:
                message.setText("🎮 **Player Menu**\n\nSelect an option:");
                keyboard.add(createRow(
                        createButton("💰 Balance", BotConstants.BALANCE),
                        createButton("🎯 Game Status", BotConstants.GAME_STATUS)
                ));
                keyboard.add(createRow(
                        createButton("🎲 Join Game", BotConstants.JOIN_GAME),
                        createButton("🃏 My Cards", BotConstants.MY_CARDS)
                ));
                keyboard.add(createRow(
                        createButton("📜 History", BotConstants.MY_HISTORY),
                        createButton("💎 Buy Points", BotConstants.BUY_POINTS)
                ));
                keyboard.add(createRow(
                        createButton("📤 Withdraw", BotConstants.WITHDRAW_REQUEST)
                ));
                keyboard.add(createRow(
                        createWebAppButton("🚀 Launch App")
                ));
                break;

            case ADMIN:
                message.setText("🎯 **Agent Dashboard**\n\nManage your games and players:");
                keyboard.add(createRow(
                        createButton("🎮 Create Game", BotConstants.CREATE_GAME),
                        createButton("▶️ Start Game", BotConstants.START_GAME)
                ));
                keyboard.add(createRow(
                        createButton("❌ Cancel Game", BotConstants.CANCEL_GAME),
                        createButton("🔔 Pending Claims", BotConstants.PENDING_CLAIMS)
                ));
                keyboard.add(createRow(
                        createButton("🔗 Invite Link", BotConstants.INVITE_LINK),
                        createButton("👥 My Players", BotConstants.MY_PLAYERS)
                ));
                keyboard.add(createRow(
                        createButton("💰 Fund Player", BotConstants.FUND_PLAYER),
                        createButton("📤 Withdrawals", BotConstants.WITHDRAW_REQUESTS)
                ));
                keyboard.add(createRow(
                        createButton("📊 Stats", BotConstants.ADMIN_STATS)
                ));
                keyboard.add(createRow(
                        createWebAppButton("🚀 Launch App")
                ));
                break;

            case SUPER_ADMIN:
                message.setText("👑 **Super Admin Panel**\n\nPlatform management:");
                keyboard.add(createRow(
                        createButton("🚀 Create Agent", BotConstants.CREATE_AGENT),
                        createButton("💰 Fund Agent", BotConstants.FUND_AGENT)
                ));
                keyboard.add(createRow(
                        createButton("📈 Reports", BotConstants.VIEW_REPORTS),
                        createButton("🎯 Active Games", BotConstants.ACTIVE_GAMES)
                ));
                keyboard.add(createRow(
                        createButton("🏢 All Agents", BotConstants.ALL_AGENTS),
                        createButton("💳 Transactions", BotConstants.TRANSACTIONS)
                ));
                keyboard.add(createRow(
                        createButton("⚙️ Settings", BotConstants.SYSTEM_SETTINGS)
                ));
                keyboard.add(createRow(
                        createWebAppButton("🚀 Launch App")
                ));
                break;
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