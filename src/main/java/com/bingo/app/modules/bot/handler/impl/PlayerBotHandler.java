package com.bingo.app.modules.bot.handler.impl;

import com.bingo.app.modules.bot.context.CallbackContext;
import com.bingo.app.modules.bot.core.BotConstants;
import com.bingo.app.modules.bot.core.TelegramBot;
import com.bingo.app.modules.bot.service.InputStateService;
import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.modules.game.service.CardService;
import com.bingo.app.modules.game.service.GameEngineService;
import com.bingo.app.modules.game.service.GameService;
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
public class PlayerBotHandler {

    private final GameService gameService;
    private final CardService cardService;
    private final GameEngineService gameEngineService;
    private final WalletService walletService;
    private final InputStateService inputStateService;

    public void handle(CallbackContext ctx) {
        String data = ctx.getData();
        log.info("PlayerBotHandler handling: {}", data);

        switch (data) {
            case BotConstants.BALANCE -> handleBalance(ctx);
            case BotConstants.GAME_STATUS -> handleGameStatus(ctx);
            case BotConstants.JOIN_GAME -> handleJoinGame(ctx);
            case BotConstants.BUY_CARD -> handleJoinGame(ctx);
            case BotConstants.CLAIM_BINGO -> handleClaimBingo(ctx);
            case BotConstants.MY_CARDS -> handleMyCards(ctx);
            case BotConstants.MY_HISTORY -> handleMyHistory(ctx);
            case BotConstants.BUY_POINTS -> handleBuyPointsPrompt(ctx);
            case BotConstants.WITHDRAW_REQUEST -> handleWithdrawPrompt(ctx);
            default -> send(ctx.getBot(), ctx.getChatId(), "Unknown action. Use the Mini App for rich gameplay!");
        }
    }

    public void handlePendingInput(CallbackContext ctx, InputStateService.PendingInput pending) {
        String text = ctx.getData();
        BigDecimal amount;
        try {
            amount = new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            send(ctx.getBot(), ctx.getChatId(), "Invalid amount. Please enter a valid number.");
            return;
        }

        switch (pending.getAction()) {
            case BUY_POINTS -> {
                try {
                    walletService.buyPoints(ctx.getUser().getId(), amount, null);
                    sendMarkdown(ctx.getBot(), ctx.getChatId(),
                            "✅ *Points Purchased*\n\nAdded `" + amount + "` points to your balance.\nNew Balance: `" + ctx.getUser().getBalance() + "`");
                } catch (Exception e) {
                    send(ctx.getBot(), ctx.getChatId(), "Failed to buy points: " + e.getMessage());
                }
            }
            case WITHDRAW_REQUEST -> {
                try {
                    walletService.createWithdrawRequest(ctx.getUser().getId(), amount, null);
                    sendMarkdown(ctx.getBot(), ctx.getChatId(),
                            "📤 *Withdraw Request Submitted*\n\nAmount: `" + amount + "`\n\nYour agent will review and approve this request.");
                } catch (Exception e) {
                    send(ctx.getBot(), ctx.getChatId(), "Failed to create withdraw request: " + e.getMessage());
                }
            }
            default -> send(ctx.getBot(), ctx.getChatId(), "Unknown action.");
        }
    }

    private void handleBalance(CallbackContext ctx) {
        String msg = "💰 *Your Balance*\n\n" +
                     "Current Points: `" + ctx.getUser().getBalance() + "`\n\n" +
                     "You can top up via your agent or join games using your points.";
        sendMarkdown(ctx.getBot(), ctx.getChatId(), msg);
    }

    private void handleGameStatus(CallbackContext ctx) {
        var user = ctx.getUser();
        if (user.getParentId() == null) {
            send(ctx.getBot(), ctx.getChatId(), "You are not assigned to any agent. Contact support.");
            return;
        }

        var gameOpt = gameService.findCurrentGameForAdmin(user.getParentId());
        if (gameOpt.isEmpty()) {
            send(ctx.getBot(), ctx.getChatId(), "No active game available. Check back later.");
            return;
        }

        var game = gameOpt.get();
        boolean hasCard = cardService.hasCardForGame(game.id(), user.getId());
        int playerCount = cardService.countCardsForGame(game.id());

        String msg = "🎯 *Game Status*\n\n" +
                     "Game ID: `" + game.id() + "`\n" +
                     "Status: *" + game.status() + "*\n" +
                     "Entry Fee: `" + game.entryFee() + "`\n" +
                     "Players: `" + playerCount + "/" + game.maxPlayers() + "`\n" +
                     "Your Card: " + (hasCard ? "✅ Registered" : "❌ Not registered");

        sendMarkdown(ctx.getBot(), ctx.getChatId(), msg);
    }

    private void handleJoinGame(CallbackContext ctx) {
        var user = ctx.getUser();
        if (user.getParentId() == null) {
            send(ctx.getBot(), ctx.getChatId(), "You are not assigned to any agent.");
            return;
        }

        var gameOpt = gameService.findCurrentGameForAdmin(user.getParentId());
        if (gameOpt.isEmpty()) {
            send(ctx.getBot(), ctx.getChatId(), "No active game available.");
            return;
        }

        var game = gameOpt.get();
        if (game.status() != GameStatus.REGISTRATION_OPEN) {
            send(ctx.getBot(), ctx.getChatId(), "The current game is not accepting new players.");
            return;
        }

        try {
            var gameCard = cardService.assignCard(game.id(), user.getId());
            sendMarkdown(ctx.getBot(), ctx.getChatId(),
                    "✅ *Joined the Game!*\n\n" +
                    "Card ID: `" + gameCard.cardId() + "`\n" +
                    "Entry Fee: `" + game.entryFee() + "`\n\n" +
                    "Open the Mini App to view your card and play!");
        } catch (Exception e) {
            send(ctx.getBot(), ctx.getChatId(), "Failed to join: " + e.getMessage());
        }
    }

    private void handleClaimBingo(CallbackContext ctx) {
        var user = ctx.getUser();
        if (user.getParentId() == null) {
            send(ctx.getBot(), ctx.getChatId(), "No active game found.");
            return;
        }

        var gameOpt = gameService.findCurrentGameForAdmin(user.getParentId());
        if (gameOpt.isEmpty()) {
            send(ctx.getBot(), ctx.getChatId(), "No active game found.");
            return;
        }

        var game = gameOpt.get();
        if (game.status() != GameStatus.IN_PROGRESS && game.status() != GameStatus.CLAIM_PENDING) {
            send(ctx.getBot(), ctx.getChatId(), "Game is not in progress.");
            return;
        }

        try {
            var winner = gameEngineService.claimBingo(game.id(), user.getId());
            sendMarkdown(ctx.getBot(), ctx.getChatId(),
                    "🎉 *BINGO! You Won!*\n\n" +
                    "Reward: `" + winner.rewardAmount() + "` points\n\n" +
                    "Congratulations!");
        } catch (Exception e) {
            send(ctx.getBot(), ctx.getChatId(), "Claim failed: " + e.getMessage());
        }
    }

    private void handleMyCards(CallbackContext ctx) {
        var cards = cardService.findCardsForPlayer(ctx.getUser().getId());
        if (cards.isEmpty()) {
            send(ctx.getBot(), ctx.getChatId(), "You have no cards registered.");
            return;
        }

        String cardList = cards.stream()
                .map(gc -> "Card #" + gc.cardId() + " | Game: " + gc.gameId() + " | Winner: " + (gc.winner() ? "✅" : "❌"))
                .collect(Collectors.joining("\n"));

        sendMarkdown(ctx.getBot(), ctx.getChatId(),
                "🃏 *My Cards (" + cards.size() + ")*\n\n" + cardList);
    }

    private void handleMyHistory(CallbackContext ctx) {
        var txs = walletService.getHistory(ctx.getUser().getId());
        if (txs.isEmpty()) {
            send(ctx.getBot(), ctx.getChatId(), "No transaction history.");
            return;
        }

        String history = txs.stream()
                .limit(10)
                .map(tx -> "• `" + tx.getType() + "` " + tx.getAmount() + " | " + tx.getStatus())
                .collect(Collectors.joining("\n"));

        sendMarkdown(ctx.getBot(), ctx.getChatId(),
                "📋 *Recent Transactions (last 10)*\n\n" + history);
    }

    private void handleBuyPointsPrompt(CallbackContext ctx) {
        inputStateService.setPendingAction(ctx.getTelegramId(), InputStateService.Action.BUY_POINTS);
        send(ctx.getBot(), ctx.getChatId(), "Enter the amount of points you want to purchase:");
    }

    private void handleWithdrawPrompt(CallbackContext ctx) {
        inputStateService.setPendingAction(ctx.getTelegramId(), InputStateService.Action.WITHDRAW_REQUEST);
        send(ctx.getBot(), ctx.getChatId(), "Enter the amount you want to withdraw:");
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
