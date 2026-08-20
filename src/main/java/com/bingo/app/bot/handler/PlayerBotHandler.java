package com.bingo.app.bot.handler;

import com.bingo.app.bot.callback.CallbackContext;
import com.bingo.app.bot.BingoTelegramBot;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.entity.Game;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.repository.GameCardRepository;
import com.bingo.app.tenant.repository.GameRepository;
import com.bingo.app.tenant.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlayerBotHandler {

    private final UserRepository userRepository;
    private final PlayerService playerService;
    private final GameRepository gameRepository;
    private final GameCardRepository gameCardRepository;

    private static final String CHECK_BALANCE = "CHECK_BALANCE";
    private static final String ACTIVE_GAME = "ACTIVE_GAME";

    public void handle(CallbackContext ctx) {
        String data = ctx.getData();
        log.info("PlayerBotHandler handling: {}", data);

        switch (data) {
            case CHECK_BALANCE -> handleCheckBalance(ctx);
            case ACTIVE_GAME -> handleActiveGame(ctx);
            default -> sendMessage(ctx.getBot(), ctx.getChatId(),
                    "Use the Launch App button to access your game and balance.");
        }
    }

    private void handleCheckBalance(CallbackContext ctx) {
        try {
            User user = userRepository.findByTelegramId(ctx.getTelegramId()).orElse(null);
            if (user == null) {
                sendMessage(ctx.getBot(), ctx.getChatId(), "Account not found. Please start the bot with /start");
                return;
            }

            BigDecimal balance = playerService.getBalance(user.getId());
            sendMessage(ctx.getBot(), ctx.getChatId(),
                    "💰 Your Balance: *" + balance + " coins*\n\nUse the Launch App to play or withdraw.");
        } catch (Exception e) {
            log.error("Failed to check balance", e);
            sendMessage(ctx.getBot(), ctx.getChatId(), "Error checking balance: " + e.getMessage());
        }
    }

    private void handleActiveGame(CallbackContext ctx) {
        try {
            User user = userRepository.findByTelegramId(ctx.getTelegramId()).orElse(null);
            if (user == null) {
                sendMessage(ctx.getBot(), ctx.getChatId(), "Account not found. Please start the bot with /start");
                return;
            }

            var activeCards = gameCardRepository.findByPlayerIdAndActiveGames(user.getId());
            if (activeCards.isEmpty()) {
                sendMessage(ctx.getBot(), ctx.getChatId(),
                        "You are not registered for any active game.\n\nUse the Launch App to join a game.");
                return;
            }

            var gameCard = activeCards.get(0);
            var game = gameRepository.findById(gameCard.getGameId()).orElse(null);
            if (game == null) {
                sendMessage(ctx.getBot(), ctx.getChatId(), "Game not found.");
                return;
            }

            String status = game.getStatus().name().replace("_", " ");
            String msg = "🎮 *Active Game*\n\n" +
                    "Game #" + game.getId() + "\n" +
                    "Status: " + status + "\n" +
                    "Entry Fee: " + game.getEntryFee() + " coins\n" +
                    "Prize Pool: " + game.getPrizePool() + " coins\n" +
                    "Numbers Called: " + game.getTotalNumbersCalled() + "/75\n\n" +
                    "Use the Launch App to view your card and claim Bingo.";

            sendMessage(ctx.getBot(), ctx.getChatId(), msg);
        } catch (Exception e) {
            log.error("Failed to check active game", e);
            sendMessage(ctx.getBot(), ctx.getChatId(), "Error: " + e.getMessage());
        }
    }

    private void sendMessage(BingoTelegramBot bot, Long chatId, String text) {
        try {
            bot.execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("Markdown")
                    .build());
        } catch (Exception e) {
            log.error("Failed to send message", e);
        }
    }
}

