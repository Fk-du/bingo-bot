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
public class AdminBotHandler {

    private final InviteService inviteService;

    public void handle(CallbackContext ctx) {
        String data = ctx.getData();
        log.info("AdminBotHandler handling: {}", data);

        switch (data) {
            case BotConstants.INVITE_LINK -> handleInviteLink(ctx);
            default -> sendMessage(ctx.getBot(), ctx.getChatId(), "Unknown action. Use the Launch App button to manage your games.");
        }
    }

    private void handleInviteLink(CallbackContext ctx) {
        try {
            String botUsername = ctx.getBot().getBotUsername();
            if (botUsername == null || botUsername.isBlank()) {
                sendMessage(ctx.getBot(), ctx.getChatId(), "Bot username not configured. Please check server configuration.");
                return;
            }
            String inviteLink = inviteService.generateInviteLinkForUser(ctx.getUser().getId(), botUsername);

            String msg = "👤 *Player Invite Link Generated*\n\n" +
                    "Share this link with players you want to recruit:\n" +
                    "`" + inviteLink + "`\n\n" +
                    "Players joining via this link will be linked to your account.";

            sendMarkdown(ctx.getBot(), ctx.getChatId(), msg);
        } catch (Exception e) {
            log.error("Failed to generate player invite link", e);
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
