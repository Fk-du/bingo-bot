package com.bingo.app.tenant.controller;

import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.tenant.dto.CreateGameRequest;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.BingoClaimResultResponse;
import com.bingo.app.tenant.dto.response.GameResponse;
import com.bingo.app.tenant.dto.response.RegisterResponse;
import com.bingo.app.tenant.enums.GameStatus;
import com.bingo.app.tenant.service.CardService;
import com.bingo.app.tenant.service.GameEngineService;
import com.bingo.app.tenant.service.GameService;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final GameEngineService gameEngineService;
    private final CardService cardService;
    private final TenantMapper tenantMapper;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GameResponse> createGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateGameRequest request) {
        var game = gameService.createGameWithEntryFee(principal.getUser().getId(), request);
        return ApiResponse.ok("Game created", tenantMapper.toDto(game));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GameResponse> startGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var game = gameService.startGameForAdmin(principal.getUser().getId(), id);
        gameEngineService.startCalling(game.getId());
        return ApiResponse.ok("Game started", tenantMapper.toDto(game));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> pauseGame(@PathVariable Long id) {
        gameEngineService.pauseGame(id);
        return ApiResponse.ok("Game paused");
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> resumeGame(@PathVariable Long id) {
        gameEngineService.resumeGame(id);
        return ApiResponse.ok("Game resumed");
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<String> endGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        gameEngineService.endGameWithoutWinner(id, "Manually ended by " + principal.getUser().getRole());
        return ApiResponse.ok("Game ended");
    }

    @GetMapping("/active")
    public ApiResponse<List<GameResponse>> activeGames(@AuthenticationPrincipal UserPrincipal principal) {
        var user = principal.getUser();
        switch (user.getRole()) {
            case ADMIN -> {
                var game = gameService.findCurrentGameForAdmin(user.getId());
                return ApiResponse.ok(game.map(g -> List.of(tenantMapper.toDto(g))).orElse(List.of()));
            }
            case PLAYER -> {
                if (user.getAgentId() == null) return ApiResponse.ok(List.of());
                var game = gameService.findCurrentGameForAdmin(user.getAgentId());
                return ApiResponse.ok(game.map(g -> List.of(tenantMapper.toDto(g))).orElse(List.of()));
            }
            default -> {
                var all = gameService.findAllGames();
                var active = all.stream()
                        .filter(g -> g.getStatus() != GameStatus.ENDED)
                        .map(tenantMapper::toDto)
                        .toList();
                return ApiResponse.ok(active);
            }
        }
    }

    @PostMapping("/{id}/register")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<RegisterResponse> register(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var gameCard = cardService.assignCard(id, principal.getUser().getId());
        return ApiResponse.ok("Registered for game", RegisterResponse.builder()
                .gameId(id)
                .cardId(gameCard.getCard().getId())
                .build());
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<BingoClaimResultResponse> claim(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) throws JsonProcessingException {
        var result = gameEngineService.claimBingo(id, principal.getUser().getId());
        return ApiResponse.ok("Bingo claim processed", BingoClaimResultResponse.builder()
                .valid(result.isValid())
                .rewardAmount(result.getRewardAmount())
                .platformFee(result.getPlatformFee())
                .agentCommission(result.getAgentCommission())
                .build());
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<GameResponse> audit(@PathVariable Long id) {
        var game = gameService.getGameById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        return ApiResponse.ok(tenantMapper.toDto(game));
    }
}
