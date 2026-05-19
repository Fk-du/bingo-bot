package com.bingo.app.modules.bot.handler.impl;

import com.bingo.app.modules.bot.context.CallbackContext;
import com.bingo.app.modules.bot.core.BotConstants;
import com.bingo.app.modules.bot.core.TelegramBot;
import com.bingo.app.modules.bot.service.InputStateService;
import com.bingo.app.modules.game.dto.CreateGameRequest;
import com.bingo.app.modules.game.service.CardService;
import com.bingo.app.modules.game.service.GameEngineService;
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
public class AdminBotHandler {

    private final GameService gameService;
    private final GameEngineService gameEngineService;
    private final CardService cardService;
    private final UserService userService;
    private final WalletService walletService;
    private final InviteService inviteService;
    private final InputStateService inputStateService;

    public void handle(CallbackContext ctx) {
        String data = ctx.getData();
        log.info("AdminBotHandler handling: {}", data);

        switch (data) {
            case BotConstants.CREATE_GAME -> handleCreateGamePrompt(ctx);
            case BotConstants.START_GAME -> handleStartGame(ctx);
            case BotConstants.CALL_NUMBER -> handleCallNumber(ctx);
            case BotConstants.INVITE_LINK -> handleInviteLink(ctx);
            case BotConstants.MY_PLAYERS -> handleMyPlayers(ctx);
            case BotConstants.FUND_PLAYER -> handleFundPlayerPrompt(ctx);
            case BotConstants.WITHDRAW_REQUESTS -> handleWithdrawRequests(ctx);
            case BotConstants.ADMIN_STATS -> handleAdminStats(ctx);
            default -> send(ctx.getBot(), ctx.getChatId(), "Unknown action.");
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
                    send(ctx.getBot(), ctx.getChatId(), "Invalid amount. Enter a valid number.");
                    return;
                }
                try {
                    var game = gameService.createGameWithEntryFee(ctx.getUser().getId(), new CreateGameRequest(entryFee, null));
                    sendMarkdown(ctx.getBot(), ctx.getChatId(),
                            "🎮 *Game Created*\n\nGame ID: `" + game.id() + "`\nEntry Fee: `" + entryFee + "`\n\nUse /start to open the admin panel and start the game.");
                } catch (Exception e) {
                    send(ctx.getBot(), ctx.getChatId(), "Failed to create game: " + e.getMessage());
                }
            }
            case FUND_PLAYER -> {
                String[] parts = text.trim().split("\\s+");
                if (parts.length < 2) {
                    send(ctx.getBot(), ctx.getChatId(), "Format: `<playerId> <amount>`\nExample: `42 100`");
                    return;
                }
                try {
                    Long playerId = Long.parseLong(parts[0]);
                    BigDecimal amount = new BigDecimal(parts[1]);
                    walletService.fundPlayer(ctx.getUser().getId(), playerId, amount);
                    sendMarkdown(ctx.getBot(), ctx.getChatId(), "✅ *Player Funded*\n\nPlayer: `" + playerId + "`\nAmount: `" + amount + "`");
                } catch (Exception e) {
                    send(ctx.getBot(), ctx.getChatId(), "Failed to fund player: " + e.getMessage());
                }
            }
            default -> send(ctx.getBot(), ctx.getChatId(), "Unknown action.");
        }
    }

    private void handleCreateGamePrompt(CallbackContext ctx) {
        inputStateService.setPendingAction(ctx.getTelegramId(), InputStateService.Action.ADMIN_CREATE_GAME_ENTRY_FEE);
        send(ctx.getBot(), ctx.getChatId(), "Enter the entry fee for the new game:");
    }

    private void handleStartGame(CallbackContext ctx) {
        try {
            var waitingGame = gameService.findAdminWaitingGame(ctx.getUser().getId());
            if (waitingGame.isEmpty()) {
                send(ctx.getBot(), ctx.getChatId(), "No waiting game found. Create a game first.");
                return;
            }
            var game = gameService.startGameForAdmin(ctx.getUser().getId(), waitingGame.get().id());
            gameEngineService.startCalling(game.id());
            sendMarkdown(ctx.getBot(), ctx.getChatId(),
                    "▶️ *Game Started*\n\nGame ID: `" + game.id() + "`\nNumbers will be called automatically.");
        } catch (Exception e) {
            send(ctx.getBot(), ctx.getChatId(), "Failed to start game: " + e.getMessage());
        }
    }

    private void handleCallNumber(CallbackContext ctx) {
        var waitingGame = gameService.findAdminWaitingGame(ctx.getUser().getId());
        var startedGame = gameService.findAdminStartedGame(ctx.getUser().getId());
        var gameOpt = startedGame.isPresent() ? startedGame : waitingGame;

        if (gameOpt.isEmpty()) {
            send(ctx.getBot(), ctx.getChatId(), "No active game found.");
            return;
        }

        try {
            Integer number = gameEngineService.callNumber(gameOpt.get().id());
            if (number != null) {
                sendMarkdown(ctx.getBot(), ctx.getChatId(),
                        "🔢 *Number Called: " + number + "*\n\nGame ID: `" + gameOpt.get().id() + "`");
            }
        } catch (Exception e) {
            send(ctx.getBot(), ctx.getChatId(), "Failed to call number: " + e.getMessage());
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

    private void handleMyPlayers(CallbackContext ctx) {
        var players = userService.findAllByParentIdAndRole(ctx.getUser().getId(), Role.PLAYER);
        if (players.isEmpty()) {
            send(ctx.getBot(), ctx.getChatId(), "You have no players yet.");
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
        send(ctx.getBot(), ctx.getChatId(), "Enter player ID and amount:\nFormat: `<playerId> <amount>`\nExample: `42 100`");
    }

    private void handleWithdrawRequests(CallbackContext ctx) {
        var requests = walletService.getPendingWithdrawsForAdminPlayers(ctx.getUser().getId());
        if (requests.isEmpty()) {
            send(ctx.getBot(), ctx.getChatId(), "No pending withdrawal requests.");
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
        int totalCards = startedGame.map(g -> cardService.countCardsForGame(g.id())).orElse(0);

        String msg = "📊 *Admin Dashboard Stats*\n\n" +
                     "Total Players: `" + totalPlayers + "`\n" +
                     "Active Players: `" + activePlayers + "`\n" +
                     "Waiting Game: " + (waitingGame.isPresent() ? "✅" : "❌") + "\n" +
                     "Started Game: " + (startedGame.isPresent() ? "✅" : "❌") + "\n" +
                     "Cards This Game: `" + totalCards + "`\n" +
                     "Your Balance: `" + ctx.getUser().getBalance() + "`";

        sendMarkdown(ctx.getBot(), ctx.getChatId(), msg);
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
