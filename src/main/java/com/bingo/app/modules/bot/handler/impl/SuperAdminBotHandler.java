package com.bingo.app.modules.bot.handler.impl;

import com.bingo.app.modules.bot.context.CallbackContext;
import com.bingo.app.modules.bot.core.BotConstants;
import com.bingo.app.modules.bot.core.TelegramBot;
import com.bingo.app.modules.bot.service.InputStateService;
import com.bingo.app.modules.game.service.CardService;
import com.bingo.app.modules.game.service.GameService;
import com.bingo.app.modules.invite.service.InviteService;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.enums.Role;
import com.bingo.app.modules.user.service.UserService;
import com.bingo.app.modules.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminBotHandler {

    private final InviteService inviteService;
    private final UserService userService;
    private final WalletService walletService;
    private final GameService gameService;
    private final CardService cardService;
    private final InputStateService inputStateService;

    public void handle(CallbackContext ctx) {
        String data = ctx.getData();
        log.info("SuperAdminBotHandler handling: {}", data);

        switch (data) {
            case BotConstants.CREATE_AGENT -> handleCreateAgent(ctx);
            case BotConstants.FUND_AGENT -> handleFundAgentPrompt(ctx);
            case BotConstants.VIEW_REPORTS -> handleViewReports(ctx);
            case BotConstants.ACTIVE_GAMES -> handleActiveGames(ctx);
            case BotConstants.ALL_AGENTS -> handleAllAgents(ctx);
            case BotConstants.TRANSACTIONS -> handleTransactions(ctx);
            case BotConstants.SYSTEM_SETTINGS -> handleSystemSettings(ctx);
            default -> send(ctx.getBot(), ctx.getChatId(), "Unknown action.");
        }
    }

    public void handlePendingInput(CallbackContext ctx, InputStateService.PendingInput pending) {
        String text = ctx.getData();

        switch (pending.getAction()) {
            case FUND_AGENT -> {
                String[] parts = text.trim().split("\\s+");
                if (parts.length < 2) {
                    send(ctx.getBot(), ctx.getChatId(), "Format: `<agentId> <amount>`\nExample: `5 1000`");
                    return;
                }
                try {
                    Long agentId = Long.parseLong(parts[0]);
                    BigDecimal amount = new BigDecimal(parts[1]);
                    walletService.fundAgent(ctx.getUser().getId(), agentId, amount);
                    sendMarkdown(ctx.getBot(), ctx.getChatId(),
                            "✅ *Agent Funded*\n\nAgent: `" + agentId + "`\nAmount: `" + amount + "`");
                } catch (Exception e) {
                    send(ctx.getBot(), ctx.getChatId(), "Failed to fund agent: " + e.getMessage());
                }
            }
            default -> send(ctx.getBot(), ctx.getChatId(), "Unknown action.");
        }
    }

    private void handleCreateAgent(CallbackContext ctx) {
        try {
            String botUsername = ctx.getBot().getBotUsername();
            String inviteLink = inviteService.generateInviteLinkForUser(ctx.getUser().getId(), botUsername);
            
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

    private void handleFundAgentPrompt(CallbackContext ctx) {
        inputStateService.setPendingAction(ctx.getTelegramId(), InputStateService.Action.FUND_AGENT);
        send(ctx.getBot(), ctx.getChatId(), "Enter agent ID and amount:\nFormat: `<agentId> <amount>`\nExample: `5 1000`");
    }

    private void handleViewReports(CallbackContext ctx) {
        var agents = userService.findAllByRole(Role.ADMIN);
        long totalAgents = agents.size();
        long totalPlayers = userService.findAllByRole(Role.PLAYER).size();
        long allTxs = walletService.getAllTransactions().size();

        String msg = "📈 *Platform Report*\n\n" +
                     "Total Agents: `" + totalAgents + "`\n" +
                     "Total Players: `" + totalPlayers + "`\n" +
                     "Total Transactions: `" + allTxs + "`\n" +
                     "Your Balance: `" + ctx.getUser().getBalance() + "`\n\n" +
                     "Detailed reports available in the Mini App.";

        sendMarkdown(ctx.getBot(), ctx.getChatId(), msg);
    }

    private void handleActiveGames(CallbackContext ctx) {
        var allGames = gameService.findAllGames();
        var activeGames = allGames.stream()
                .filter(g -> g.getStatus() == com.bingo.app.modules.game.enums.GameStatus.REGISTRATION_OPEN
                        || g.getStatus() == com.bingo.app.modules.game.enums.GameStatus.IN_PROGRESS
                        || g.getStatus() == com.bingo.app.modules.game.enums.GameStatus.CLAIM_PENDING)
                .toList();

        if (activeGames.isEmpty()) {
            send(ctx.getBot(), ctx.getChatId(), "No active games across the platform.");
            return;
        }

        String gameList = activeGames.stream()
                .map(g -> "• Game #" + g.getId() + " | Admin: `" + g.getAdminId() + "` | Status: *" + g.getStatus() + "* | Players: `" + cardService.countCardsForGame(g.getId()) + "`")
                .collect(Collectors.joining("\n"));

        sendMarkdown(ctx.getBot(), ctx.getChatId(),
                "🎯 *Active Games (" + activeGames.size() + ")*\n\n" + gameList);
    }

    private void handleAllAgents(CallbackContext ctx) {
        var agents = userService.findAllByRole(Role.ADMIN);
        if (agents.isEmpty()) {
            send(ctx.getBot(), ctx.getChatId(), "No agents registered yet.");
            return;
        }

        String agentList = agents.stream()
                .map(a -> "• ID: `" + a.getId() + "` | Balance: `" + a.getBalance() + "` | Players: " + userService.findAllByParentIdAndRole(a.getId(), Role.PLAYER).size() + (a.isActive() ? " ✅" : " ❌"))
                .collect(Collectors.joining("\n"));

        sendMarkdown(ctx.getBot(), ctx.getChatId(),
                "🏢 *All Agents (" + agents.size() + ")*\n\n" + agentList);
    }

    private void handleTransactions(CallbackContext ctx) {
        var txs = walletService.getAllTransactions();
        if (txs.isEmpty()) {
            send(ctx.getBot(), ctx.getChatId(), "No transactions found.");
            return;
        }

        String txList = txs.stream()
                .limit(10)
                .map(tx -> "• #" + tx.getId() + " | User: `" + tx.getUserId() + "` | " + tx.getType() + " | `" + tx.getAmount() + "` | " + tx.getStatus())
                .collect(Collectors.joining("\n"));

        sendMarkdown(ctx.getBot(), ctx.getChatId(),
                "💳 *Latest Transactions (last 10)*\n\n" + txList);
    }

    private void handleSystemSettings(CallbackContext ctx) {
        send(ctx.getBot(), ctx.getChatId(),
                "⚙️ System Settings\n\nUse the Mini App (Super Admin Panel) to configure:\n" +
                "• Platform fee rates\n" +
                "• Agent commission rates\n" +
                "• Bot configuration\n" +
                "• System preferences");
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
