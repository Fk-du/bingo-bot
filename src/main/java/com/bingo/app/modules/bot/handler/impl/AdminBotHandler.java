package com.bingo.app.modules.bot.handler.impl;

import com.bingo.app.modules.bot.context.CallbackContext;
import com.bingo.app.modules.bot.core.BotConstants;
import com.bingo.app.modules.bot.core.TelegramBot;
import com.bingo.app.modules.invite.service.InviteService;
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

        if (data.equals(BotConstants.INVITE_LINK)) {
            handleInviteLink(ctx);
        } else {
            send(ctx.getBot(), ctx.getChatId(), "This feature is not yet implemented in the Admin dashboard.");
        }
    }

    private void handleInviteLink(CallbackContext ctx) {
        try {
            String botUsername = ctx.getBot().getBotUsername();
            String inviteLink = inviteService.generateInviteLinkForUser(ctx.getUser().getId(), botUsername);
            
            String msg = "👤 *Player Invite Link Generated*\n\n" +
                         "Share this link with players you want to recruit:\n" +
                         "`" + inviteLink + "`\n\n" +
                         "Players joining via this link will be linked to your account.";
            
            sendMarkdown(ctx.getBot(), ctx.getChatId(), msg);
        } catch (Exception e) {
            log.error("Failed to generate player invite link", e);
            send(ctx.getBot(), ctx.getChatId(), "Error generating invite link. Please try again.");
        }
    }

    private void send(TelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .build());
        } catch (Exception e) {
            log.error("Failed to send message", e);
        }
    }

    private void sendMarkdown(TelegramBot bot, Long chatId, String text) {
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
