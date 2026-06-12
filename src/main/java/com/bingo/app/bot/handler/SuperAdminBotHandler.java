package com.bingo.app.bot.handler;

import com.bingo.app.master.service.InviteService;
import com.bingo.app.bot.callback.CallbackContext;
import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.bot.BotConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminBotHandler {

    private final InviteService inviteService;

    public void handle(CallbackContext ctx) {
        String data = ctx.getData();
        log.info("SuperAdminBotHandler handling: {}", data);

        switch (data) {
            case BotConstants.CREATE_ADMIN -> handleCreateAdmin(ctx);
            default -> sendMessage(ctx.getBot(), ctx.getChatId(), "Unknown action. Use the Launch App button to manage the platform.");
        }
    }

    private void handleCreateAdmin(CallbackContext ctx) {
        try {
            String botUsername = ctx.getBot().getBotUsername();
            if (botUsername == null || botUsername.isBlank()) {
                sendMessage(ctx.getBot(), ctx.getChatId(), "Bot username not configured. Please check server configuration.");
                return;
            }
            String inviteLink = inviteService.generateInviteLinkForUser(ctx.getUser().getId(), botUsername);

            String msg = "🚀 *New Admin Invite Link Generated*\n\n" +
                    "Share this link with the person who will become an admin:\n" +
                    "`" + inviteLink + "`\n\n" +
                    "Once they click start, they will be registered as an ADMIN under your supervision.";

            sendMarkdown(ctx.getBot(), ctx.getChatId(), msg);
        } catch (Exception e) {
            log.error("Failed to generate admin invite link", e);
            sendMessage(ctx.getBot(), ctx.getChatId(), "Error: " + e.getMessage());
        }
    }

    private void sendMessage(BingoTelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build());
        } catch (Exception e) {
            log.error("Failed to send message", e);
        }
    }

    private void sendMarkdown(BingoTelegramBot bot, Long chatId, String text) {
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
