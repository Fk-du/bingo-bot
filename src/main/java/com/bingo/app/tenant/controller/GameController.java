package com.bingo.app.tenant.controller;

import com.bingo.app.common.util.AdminIds;
import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.tenant.dto.CreateGameRequest;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.BingoClaimResponse;
import com.bingo.app.tenant.dto.response.BingoClaimResultResponse;
import com.bingo.app.tenant.dto.response.GameResponse;
import com.bingo.app.tenant.dto.response.GameStateResponse;
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

    @GetMapping("/{id}/state")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<GameStateResponse> getGameState(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var state = gameEngineService.getGameState(id, principal.getUser().getId());
        return ApiResponse.ok(tenantMapper.toGameStateDto(state));
    }

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

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> cancelGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        gameService.cancelGame(id, principal.getUser().getId());
        return ApiResponse.ok("Game cancelled");
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
    @PreAuthorize("hasAnyRole('ADMIN')")
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
                Long adminUserId = AdminIds.adminUserId(user);
                if (adminUserId == null) return ApiResponse.ok(List.of());
                var game = gameService.findCurrentGameForAdmin(adminUserId);
                return ApiResponse.ok(game.map(g -> List.of(tenantMapper.toDto(g))).orElse(List.of()));
            }
            default -> {
                return ApiResponse.ok(List.of());
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
        String message = result.isPendingReview()
                ? "Bingo claimed! Waiting for admin review."
                : "Bingo claim processed";
        return ApiResponse.ok(message, BingoClaimResultResponse.builder()
                .valid(result.isValid())
                .claimId(result.getClaimId())
                .pendingReview(result.isPendingReview())
                .rewardAmount(result.getRewardAmount())
                .platformFee(result.getPlatformFee())
                .agentCommission(result.getAgentCommission())
                .build());
    }

    @GetMapping("/{id}/claims/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<BingoClaimResponse>> getPendingClaims(@PathVariable Long id) {
        var claims = gameEngineService.getPendingClaims(id);
        var dtos = claims.stream().map(tenantMapper::toDto).toList();
        return ApiResponse.ok(dtos);
    }

    @PostMapping("/{id}/claims/{claimId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BingoClaimResultResponse> approveClaim(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long claimId) {
        var result = gameEngineService.approveClaim(id, claimId, principal.getUser().getId());
        String message = result.isGameEnded()
                ? "Claim approved, winner paid. Game ended (max winners reached)."
                : "Claim approved, winner paid.";
        return ApiResponse.ok(message, BingoClaimResultResponse.builder()
                .valid(true)
                .claimId(result.getClaimId())
                .pendingReview(false)
                .gameEnded(result.isGameEnded())
                .approvedCount(result.getApprovedCount())
                .rewardAmount(result.getRewardAmount())
                .platformFee(result.getPlatformFee())
                .agentCommission(result.getAgentCommission())
                .build());
    }

    @PostMapping("/{id}/claims/{claimId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> rejectClaim(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long claimId,
            @RequestParam(defaultValue = "Claim rejected by admin") String reason) {
        gameEngineService.rejectClaim(id, claimId, principal.getUser().getId(), reason);
        return ApiResponse.ok("Claim rejected, game resumed");
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<GameResponse> audit(@PathVariable Long id) {
        var game = gameService.getGameById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        return ApiResponse.ok(tenantMapper.toDto(game));
    }
}
