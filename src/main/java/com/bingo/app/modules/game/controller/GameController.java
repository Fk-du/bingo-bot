package com.bingo.app.modules.game.controller;

import com.bingo.app.modules.game.dto.BingoClaimResponse;
import com.bingo.app.modules.game.dto.GameCardResponse;
import com.bingo.app.modules.game.dto.GameResponse;
import com.bingo.app.modules.game.dto.WinnerResponse;
import com.bingo.app.modules.game.repository.BingoClaimRepository;
import com.bingo.app.modules.game.service.CardService;
import com.bingo.app.modules.game.service.GameEngineService;
import com.bingo.app.modules.game.service.GameService;
import com.bingo.app.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final CardService cardService;
    private final GameEngineService gameEngineService;
    private final BingoClaimRepository bingoClaimRepository;

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GameResponse> getActiveGame(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.noContent().build();
        }

        Optional<GameResponse> activeGame = switch (user.getRole()) {
            case PLAYER -> user.getParentId() == null
                    ? Optional.empty()
                    : gameService.findCurrentGameForAdmin(user.getParentId());
            case ADMIN -> gameService.findCurrentGameForAdmin(user.getId());
            case SUPER_ADMIN -> gameService.findCurrentGame();
        };

        return activeGame.map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/{gameId}/register")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<GameCardResponse> registerForGame(
            @PathVariable Long gameId,
            @RequestParam Long cardId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(cardService.assignCard(gameId, user.getId(), cardId));
    }

    @PostMapping("/{gameId}/claim")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<WinnerResponse> claimBingo(@PathVariable Long gameId, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(gameEngineService.claimBingo(gameId, user.getId()));
    }

    @GetMapping("/{gameId}/audit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BingoClaimResponse>> getGameAudit(@PathVariable Long gameId) {
        return ResponseEntity.ok(
                bingoClaimRepository.findByGameId(gameId).stream()
                        .map(BingoClaimResponse::from)
                        .toList()
        );
    }
}
