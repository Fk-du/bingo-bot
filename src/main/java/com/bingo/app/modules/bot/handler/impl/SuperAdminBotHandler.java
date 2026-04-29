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
public class SuperAdminBotHandler {

    private final InviteService inviteService;

    public void handle(CallbackContext ctx) {
        String data = ctx.getData();
        log.info("SuperAdminBotHandler handling: {}", data);

        if (data.equals(BotConstants.CREATE_AGENT)) {
            handleCreateAgent(ctx);
        } else {
            send(ctx.getBot(), ctx.getChatId(), "This feature is not yet implemented in the Super Admin dashboard.");
        }
    }

    private void handleCreateAgent(CallbackContext ctx) {
        try {
            String botUsername = ctx.getBot().getBotUsername();
            String inviteLink = inviteService.generateInviteLink(ctx.getUser().getId(), botUsername);
            
            String msg = "🚀 *New Agent Invite Link Generated*\n\n" +
                         "Share this link with the person who will become an agent:\n" +
                         "`" + inviteLink + "`\n\n" +
                         "Once they click start, they will be registered as an ADMIN under your supervision.";
            
            sendMarkdown(ctx.getBot(), ctx.getChatId(), msg);
        } catch (Exception e) {
            log.error("Failed to generate agent invite link", e);
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
