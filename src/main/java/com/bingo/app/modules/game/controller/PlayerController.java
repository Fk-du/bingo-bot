package com.bingo.app.modules.game.controller;

import com.bingo.app.modules.game.entity.Game;
import com.bingo.app.modules.game.entity.GameCard;
import com.bingo.app.modules.game.service.GameService;
import com.bingo.app.modules.game.service.CardService;
import com.bingo.app.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.bingo.app.modules.game.service.GameEngineService;
import com.bingo.app.modules.wallet.entity.Transaction;
import com.bingo.app.modules.wallet.service.WalletService;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/player")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLAYER')")
public class PlayerController {

    private final GameService gameService;
    private final CardService cardService;
    private final GameEngineService gameEngineService;
    private final WalletService walletService;

    @GetMapping("/game/current")
    public ResponseEntity<Game> getCurrentGame() {
        return gameService.findCurrentGame()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/game/{gameId}/register")
    public ResponseEntity<Void> registerForGame(@PathVariable Long gameId, @AuthenticationPrincipal User user) {
        // Logic to register before game starts
        return ResponseEntity.ok().build();
    }

    @GetMapping("/game/{gameId}/available-cards")
    public ResponseEntity<List<Object>> getAvailableCards(@PathVariable Long gameId) {
        // return ResponseEntity.ok(cardService.getUnassignedCards(gameId));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/game/{gameId}/join")
    public ResponseEntity<GameCard> joinGame(@PathVariable Long gameId, @AuthenticationPrincipal User user) {
        GameCard gameCard = cardService.assignCard(gameId, user.getId());
        return ResponseEntity.ok(gameCard);
    }

    @PostMapping("/game/{gameId}/bingo/claim")
    public ResponseEntity<Void> claimBingo(@PathVariable Long gameId, @AuthenticationPrincipal User user) {
        gameEngineService.claimBingo(gameId, user.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my-cards")
    public ResponseEntity<List<GameCard>> getMyCards(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(cardService.findCardsForPlayer(user.getId()));
    }

    @GetMapping("/balance")
    public ResponseEntity<BigDecimal> getBalance(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(user.getBalance());
    }

    @PostMapping("/points/buy")
    public ResponseEntity<Transaction> buyPoints(@AuthenticationPrincipal User user, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(walletService.buyPoints(user.getId(), amount, null));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<Transaction> withdrawRequest(@AuthenticationPrincipal User user, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(walletService.createWithdrawRequest(user.getId(), amount, null));
    }
}
