package com.bingo.app.bot.handler;

import com.bingo.app.tenant.dto.CreateGameRequest;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.service.InviteService;
import com.bingo.app.master.service.UserService;
import com.bingo.app.bot.callback.CallbackContext;
import com.bingo.app.bot.service.InputStateService;
import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.bot.BotConstants;
import com.bingo.app.tenant.entity.BingoClaim;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.repository.BingoClaimRepository;
import com.bingo.app.tenant.service.CardService;
import com.bingo.app.tenant.service.GameEngineService;
import com.bingo.app.tenant.service.GameService;
import com.bingo.app.tenant.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBotHandler {

    private final GameService gameService;
    private final GameEngineService gameEngineService;
    private final CardService cardService;
    private final UserService userService;
    private final WalletService walletService;
    private final InviteService inviteService;
    private final InputStateService inputStateService;
    private final BingoClaimRepository bingoClaimRepository;

    public void handle(CallbackContext ctx) {
        String data = ctx.getData();
        log.info("AdminBotHandler handling: {}", data);

        // Check for claim-specific actions with embedded claim ID
        if (data.startsWith("APPROVE_CLAIM:")) {
            handleApproveClaim(ctx, data.substring("APPROVE_CLAIM:".length()));
            return;
        }
        if (data.startsWith("REJECT_CLAIM:")) {
            handleRejectClaim(ctx, data.substring("REJECT_CLAIM:".length()));
            return;
        }

        switch (data) {
            case BotConstants.CREATE_GAME -> handleCreateGamePrompt(ctx);
            case BotConstants.START_GAME -> handleStartGame(ctx);
            case BotConstants.CANCEL_GAME -> handleCancelGame(ctx);
            case BotConstants.INVITE_LINK -> handleInviteLink(ctx);
            case BotConstants.MY_PLAYERS -> handleMyPlayers(ctx);
            case BotConstants.FUND_PLAYER -> handleFundPlayerPrompt(ctx);
            case BotConstants.WITHDRAW_REQUESTS -> handleWithdrawRequests(ctx);
            case BotConstants.ADMIN_STATS -> handleAdminStats(ctx);
            case BotConstants.PENDING_CLAIMS -> handlePendingClaims(ctx);
            default -> sendMessage(ctx.getBot(), ctx.getChatId(), "Unknown action.");
        }
    }

    public void handlePendingInput(CallbackContext ctx, InputStateService.PendingInput pending) {
        String text = ctx.getData();

        switch (pending.getAction()) {
            case ADMIN_CREATE_GAME_ENTRY_FEE -> {
                BigDecimal entryFee;
                try {
                    entryFee = new BigDecimal(text.trim());
                } catch (NumberFormatException e) {
                    sendMessage(ctx.getBot(), ctx.getChatId(), "Invalid amount. Enter a valid number.");
                    return;
                }
                try {
                    var game = gameService.createGameWithEntryFee(ctx.getUser().getId(),
                            CreateGameRequest.builder().entryFee(entryFee).build());
                    sendMarkdown(ctx.getBot(), ctx.getChatId(),
                            "🎮 *Game Created*\n\nGame ID: `" + game.getId() + "`\nEntry Fee: `" + entryFee + "`\n\nUse the menu to start the game.");
                } catch (Exception e) {
                    sendMessage(ctx.getBot(), ctx.getChatId(), "Failed to create game: " + e.getMessage());
                }
            }
            case FUND_PLAYER -> {
                String[] parts = text.trim().split("\\s+");
                if (parts.length < 2) {
                    sendMessage(ctx.getBot(), ctx.getChatId(), "Format: `<playerId> <amount>`\nExample: `42 100`");
                    return;
                }
                try {
                    Long playerId = Long.parseLong(parts[0]);
                    BigDecimal amount = new BigDecimal(parts[1]);
                    walletService.fundPlayer(ctx.getUser().getId(), playerId, amount);
                    sendMarkdown(ctx.getBot(), ctx.getChatId(),
                            "✅ *Player Funded*\n\nPlayer: `" + playerId + "`\nAmount: `" + amount + "`");
                } catch (Exception e) {
                    sendMessage(ctx.getBot(), ctx.getChatId(), "Failed to fund player: " + e.getMessage());
                }
            }
            default -> sendMessage(ctx.getBot(), ctx.getChatId(), "Unknown action.");
        }
    }

    private void handleCreateGamePrompt(CallbackContext ctx) {
        inputStateService.setPendingAction(ctx.getTelegramId(), InputStateService.Action.ADMIN_CREATE_GAME_ENTRY_FEE);
        sendMessage(ctx.getBot(), ctx.getChatId(), "Enter the entry fee for the new game:");
    }

    private void handleStartGame(CallbackContext ctx) {
        try {
            var waitingGame = gameService.findAdminWaitingGame(ctx.getUser().getId());
            if (waitingGame.isEmpty()) {
                sendMessage(ctx.getBot(), ctx.getChatId(), "No waiting game found. Create a game first.");
                return;
            }
            var game = gameService.startGameForAdmin(ctx.getUser().getId(), waitingGame.get().getId());
            gameEngineService.startCalling(game.getId());
            sendMarkdown(ctx.getBot(), ctx.getChatId(),
                    "▶️ *Game Started*\n\nGame ID: `" + game.getId() + "`\nNumbers will be called automatically.");
        } catch (Exception e) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "Failed to start game: " + e.getMessage());
        }
    }

    private void handleCancelGame(CallbackContext ctx) {
        try {
            var waitingGame = gameService.findAdminWaitingGame(ctx.getUser().getId());
            if (waitingGame.isEmpty()) {
                sendMessage(ctx.getBot(), ctx.getChatId(), "No waiting game to cancel.");
                return;
            }
            gameService.cancelGame(waitingGame.get().getId(), ctx.getUser().getId());
            sendMarkdown(ctx.getBot(), ctx.getChatId(),
                    "❌ *Game Cancelled*\n\nGame ID: `" + waitingGame.get().getId() + "`");
        } catch (Exception e) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "Failed to cancel game: " + e.getMessage());
        }
    }

    private void handlePendingClaims(CallbackContext ctx) {
        try {
            var gameOpt = gameService.findCurrentGameForAdmin(ctx.getUser().getId());
            if (gameOpt.isEmpty()) {
                sendMessage(ctx.getBot(), ctx.getChatId(), "No active game.");
                return;
            }

            var game = gameOpt.get();
            if (game.getStatus() != GameStatus.CLAIM_PENDING) {
                sendMessage(ctx.getBot(), ctx.getChatId(), "No pending claims for this game.");
                return;
            }

            List<BingoClaim> pendingClaims = bingoClaimRepository
                    .findByGameIdAndResult(game.getId(), "VALID");

            if (pendingClaims.isEmpty()) {
                sendMessage(ctx.getBot(), ctx.getChatId(), "No VALID claims waiting for review.");
                return;
            }

            for (BingoClaim claim : pendingClaims) {
                String msg = "🔔 *Pending Bingo Claim*\n\n" +
                        "Claim ID: `" + claim.getId() + "`\n" +
                        "Player ID: `" + claim.getPlayerId() + "`\n" +
                        "Claimed at: `" + claim.getClaimedAt() + "`";

                InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();

                InlineKeyboardButton approveBtn = new InlineKeyboardButton();
                approveBtn.setText("✅ Approve");
                approveBtn.setCallbackData("APPROVE_CLAIM:" + claim.getId());

                InlineKeyboardButton rejectBtn = new InlineKeyboardButton();
                rejectBtn.setText("❌ Reject");
                rejectBtn.setCallbackData("REJECT_CLAIM:" + claim.getId());

                keyboard.setKeyboard(List.of(List.of(approveBtn, rejectBtn)));

                var message = SendMessage.builder()
                        .chatId(ctx.getChatId().toString())
                        .text(msg)
                        .parseMode("Markdown")
                        .replyMarkup(keyboard)
                        .build();
                ctx.getBot().execute(message);
            }
        } catch (Exception e) {
            log.error("Failed to show pending claims", e);
            sendMessage(ctx.getBot(), ctx.getChatId(), "Failed to load pending claims: " + e.getMessage());
        }
    }

    private void handleApproveClaim(CallbackContext ctx, String claimIdStr) {
        try {
            Long claimId = Long.parseLong(claimIdStr);
            var gameOpt = gameService.findCurrentGameForAdmin(ctx.getUser().getId());
            if (gameOpt.isEmpty()) {
                sendMessage(ctx.getBot(), ctx.getChatId(), "No active game found.");
                return;
            }

            var result = gameEngineService.approveClaim(gameOpt.get().getId(), claimId, ctx.getUser().getId());
            sendMarkdown(ctx.getBot(), ctx.getChatId(),
                    "✅ *Claim Approved!*\n\n" +
                            "Winner Paid: `" + result.getRewardAmount() + "`\n" +
                            "Platform Fee: `" + result.getPlatformFee() + "`\n" +
                            "Agent Commission: `" + result.getAgentCommission() + "`\n\n" +
                            "Game has ended.");
        } catch (Exception e) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "Failed to approve claim: " + e.getMessage());
        }
    }

    private void handleRejectClaim(CallbackContext ctx, String claimIdStr) {
        try {
            Long claimId = Long.parseLong(claimIdStr);
            var gameOpt = gameService.findCurrentGameForAdmin(ctx.getUser().getId());
            if (gameOpt.isEmpty()) {
                sendMessage(ctx.getBot(), ctx.getChatId(), "No active game found.");
                return;
            }

            gameEngineService.rejectClaim(gameOpt.get().getId(), claimId, ctx.getUser().getId(), "Rejected by admin");
            sendMarkdown(ctx.getBot(), ctx.getChatId(),
                    "❌ *Claim Rejected*\n\nGame has resumed.");
        } catch (Exception e) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "Failed to reject claim: " + e.getMessage());
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
            sendMessage(ctx.getBot(), ctx.getChatId(), "Error generating invite link. Please try again.");
        }
    }

    private void handleMyPlayers(CallbackContext ctx) {
        var players = userService.findAllByParentIdAndRole(ctx.getUser().getId(), Role.PLAYER);
        if (players.isEmpty()) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "You have no players yet.");
            return;
        }

        String playerList = players.stream()
                .map(p -> "• ID: `" + p.getId() + "` | Balance: `" + p.getBalance() + "`" + (p.isActive() ? " ✅" : " ❌"))
                .collect(Collectors.joining("\n"));

        sendMarkdown(ctx.getBot(), ctx.getChatId(),
                "👥 *My Players (" + players.size() + ")*\n\n" + playerList);
    }

    private void handleFundPlayerPrompt(CallbackContext ctx) {
        inputStateService.setPendingAction(ctx.getTelegramId(), InputStateService.Action.FUND_PLAYER);
        sendMessage(ctx.getBot(), ctx.getChatId(), "Enter player ID and amount:\nFormat: `<playerId> <amount>`\nExample: `42 100`");
    }

    private void handleWithdrawRequests(CallbackContext ctx) {
        var requests = walletService.getPendingWithdrawsForAdminPlayers(ctx.getUser().getId());
        if (requests.isEmpty()) {
            sendMessage(ctx.getBot(), ctx.getChatId(), "No pending withdrawal requests.");
            return;
        }

        String list = requests.stream()
                .map(r -> "• Request #" + r.getId() + " | Player: `" + r.getUserId() + "` | Amount: `" + r.getAmount() + "`")
                .collect(Collectors.joining("\n"));

        sendMarkdown(ctx.getBot(), ctx.getChatId(),
                "📤 *Pending Withdrawals (" + requests.size() + ")*\n\n" + list +
                        "\n\nUse the Mini App to approve or reject requests.");
    }

    private void handleAdminStats(CallbackContext ctx) {
        var players = userService.findAllByParentIdAndRole(ctx.getUser().getId(), Role.PLAYER);
        var waitingGame = gameService.findAdminWaitingGame(ctx.getUser().getId());
        var startedGame = gameService.findAdminStartedGame(ctx.getUser().getId());

        long totalPlayers = players.size();
        long activePlayers = players.stream().filter(User::isActive).count();
        int totalCards = startedGame.map(g -> cardService.countCardsForGame(g.getId())).orElse(0);

        String msg = "📊 *Admin Dashboard Stats*\n\n" +
                "Total Players: `" + totalPlayers + "`\n" +
                "Active Players: `" + activePlayers + "`\n" +
                "Waiting Game: " + (waitingGame.isPresent() ? "✅" : "❌") + "\n" +
                "Started Game: " + (startedGame.isPresent() ? "✅" : "❌") + "\n" +
                "Cards This Game: `" + totalCards + "`\n" +
                "Your Balance: `" + ctx.getUser().getBalance() + "`";

        sendMarkdown(ctx.getBot(), ctx.getChatId(), msg);
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