package com.bingo.app.bot.handler;

import com.bingo.app.bot.callback.CallbackContext;
import com.bingo.app.bot.service.InputStateService;
import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.bot.BotConstants;
import com.bingo.app.tenant.service.CardService;
import com.bingo.app.tenant.service.GameEngineService;
import com.bingo.app.tenant.service.GameService;
import com.bingo.app.tenant.service.WalletService;
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
            case BotConstants.BUY_CARD -> handleBuyCard(ctx);
            case BotConstants.CLAIM_BINGO -> handleClaimBingo(ctx);
            case BotConstants.MY_CARDS -> handleMyCards(ctx);
            case BotConstants.MY_HISTORY -> handleMyHistory(ctx);
            case BotConstants.BUY_POINTS -> handleBuyPointsPrompt(ctx);
            case BotConstants.WITHDRAW_REQUEST -> handleWithdrawPrompt(ctx);
            default -> sendMessage(ctx.getBot(), ctx.getChatId(), "Unknown action. Use the Mini App for rich gameplay!");
        }
    }

    public void handlePendingInput(CallbackContext ctx, InputStateService.PendingInput pending) {
        String text = ctx.getData();
        BigDecimal amount;
        try {
            amount = new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "Invalid amount. Please enter a valid number.");
            return;
        }

        switch (pending.getAction()) {
            case BUY_POINTS -> {
                try {
                    walletService.buyPoints(ctx.getUser().getId(), amount, null);
                    sendMarkdown(ctx.getBot(), ctx.getChatId(),
                            "✅ *Points Purchased*\n\nAdded `" + amount + "` points to your balance.\nNew Balance: `" + ctx.getUser().getBalance() + "`");
                } catch (Exception e) {
                    sendMessage(ctx.getBot(), ctx.getChatId(), "Failed to buy points: " + e.getMessage());
                }
            }
            case WITHDRAW_REQUEST -> {
                try {
                    walletService.createWithdrawRequest(ctx.getUser().getId(), amount, null);
                    sendMarkdown(ctx.getBot(), ctx.getChatId(),
                            "📤 *Withdraw Request Submitted*\n\nAmount: `" + amount + "`\n\nYour agent will review and approve this request.");
                } catch (Exception e) {
                    sendMessage(ctx.getBot(), ctx.getChatId(), "Failed to create withdraw request: " + e.getMessage());
                }
            }
            default -> sendMessage(ctx.getBot(), ctx.getChatId(), "Unknown action.");
        }
    }

    private void handleBalance(CallbackContext ctx) {
        String msg = "💰 *Your Balance*\n\n" +
                "Current Points: `" + ctx.getUser().getBalance() + "`\n" +
                "Frozen Points: `" + ctx.getUser().getFrozenBalance() + "`\n\n" +
                "You can top up via your agent or join games using your points.";
        sendMarkdown(ctx.getBot(), ctx.getChatId(), msg);
    }

    private void handleGameStatus(CallbackContext ctx) {
        var user = ctx.getUser();
        if (user.getAgentId() == null) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "You are not assigned to any agent. Contact support.");
            return;
        }

        var gameOpt = gameService.findCurrentGameForAdmin(user.getAgentId());
        if (gameOpt.isEmpty()) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "No active game available. Check back later.");
            return;
        }

        var game = gameOpt.get();
        boolean hasCard = cardService.hasCardForGame(game.getId(), user.getId());
        int playerCount = cardService.countCardsForGame(game.getId());

        String msg = "🎯 *Game Status*\n\n" +
                "Game ID: `" + game.getId() + "`\n" +
                "Status: *" + game.getStatus() + "*\n" +
                "Entry Fee: `" + game.getEntryFee() + "`\n" +
                "Players: `" + playerCount + "/" + game.getMaxPlayers() + "`\n" +
                "Your Card: " + (hasCard ? "✅ Registered" : "❌ Not registered");

        sendMarkdown(ctx.getBot(), ctx.getChatId(), msg);
    }

    private void handleJoinGame(CallbackContext ctx) {
        var user = ctx.getUser();
        if (user.getAgentId() == null) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "You are not assigned to any agent.");
            return;
        }

        var gameOpt = gameService.findCurrentGameForAdmin(user.getAgentId());
        if (gameOpt.isEmpty()) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "No active game available.");
            return;
        }

        var game = gameOpt.get();
        if (game.getStatus() != com.bingo.app.tenant.enums.GameStatus.REGISTRATION_OPEN) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "The current game is not accepting new players.");
            return;
        }

        try {
            var gameCard = cardService.assignCard(game.getId(), user.getId());
            sendMarkdown(ctx.getBot(), ctx.getChatId(),
                    "✅ *Joined the Game!*\n\n" +
                            "Card ID: `" + gameCard.getCard().getId() + "`\n" +
                            "Entry Fee: `" + game.getEntryFee() + "`\n\n" +
                            "Open the Mini App to view your card and play!");
        } catch (Exception e) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "Failed to join: " + e.getMessage());
        }
    }

    private void handleBuyCard(CallbackContext ctx) {
        handleJoinGame(ctx); // Same logic as join game
    }

    private void handleClaimBingo(CallbackContext ctx) {
        var user = ctx.getUser();
        if (user.getAgentId() == null) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "No active game found.");
            return;
        }

        var gameOpt = gameService.findCurrentGameForAdmin(user.getAgentId());
        if (gameOpt.isEmpty()) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "No active game found.");
            return;
        }

        var game = gameOpt.get();
        if (game.getStatus() != com.bingo.app.tenant.enums.GameStatus.IN_PROGRESS) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "Game is not in progress.");
            return;
        }

        try {
            var result = gameEngineService.claimBingo(game.getId(), user.getId());
            if (result.isPendingReview()) {
                sendMarkdown(ctx.getBot(), ctx.getChatId(),
                        "🔔 *BINGO Claimed!*\n\n" +
                                "Your claim is pending admin review.\n" +
                                "Please wait for the admin to verify and approve your win.\n\n" +
                                "Claim ID: `" + result.getClaimId() + "`");
            } else {
                sendMarkdown(ctx.getBot(), ctx.getChatId(),
                        "🎉 *BINGO! You Won!*\n\n" +
                                "Reward: `" + result.getRewardAmount() + "` points\n\n" +
                                "Congratulations!");
            }
        } catch (Exception e) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "Claim failed: " + e.getMessage());
        }
    }

    private void handleMyCards(CallbackContext ctx) {
        var cards = cardService.findCardsForPlayer(ctx.getUser().getId());
        if (cards.isEmpty()) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "You have no cards registered.");
            return;
        }

        String cardList = cards.stream()
                .map(gc -> "Card #" + gc.getCard().getId() + " | Game: " + gc.getGameId() + " | Winner: " + (gc.isWinner() ? "✅" : "❌"))
                .collect(Collectors.joining("\n"));

        sendMarkdown(ctx.getBot(), ctx.getChatId(),
                "🃏 *My Cards (" + cards.size() + ")*\n\n" + cardList);
    }

    private void handleMyHistory(CallbackContext ctx) {
        var txs = walletService.getHistory(ctx.getUser().getId());
        if (txs.isEmpty()) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "No transaction history.");
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
        sendMessage(ctx.getBot(), ctx.getChatId(), "Enter the amount of points you want to purchase:");
    }

    private void handleWithdrawPrompt(CallbackContext ctx) {
        inputStateService.setPendingAction(ctx.getTelegramId(), InputStateService.Action.WITHDRAW_REQUEST);
        sendMessage(ctx.getBot(), ctx.getChatId(), "Enter the amount you want to withdraw:");
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