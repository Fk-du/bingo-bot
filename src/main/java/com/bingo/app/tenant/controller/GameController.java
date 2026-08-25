package com.bingo.app.tenant.controller;

import com.bingo.app.common.util.AdminIds;
import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.tenant.dto.CreateGameRequest;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.request.ClaimBingoRequest;
import com.bingo.app.tenant.dto.request.GameSettingsUpdateRequest;
import com.bingo.app.tenant.dto.response.AdminGameStateResponse;
import com.bingo.app.tenant.dto.response.BingoClaimResponse;
import com.bingo.app.tenant.dto.response.BingoClaimResultResponse;
import com.bingo.app.tenant.dto.response.GameResponse;
import com.bingo.app.tenant.dto.response.GameStateResponse;
import com.bingo.app.tenant.dto.response.RegisterResponse;
import com.bingo.app.master.enums.Role;
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
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    public ApiResponse<?> getGameState(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var role = principal.getUser().getRole();
        if (role == Role.ADMIN) {
            var state = gameEngineService.getAdminGameState(id);
            return ApiResponse.ok(tenantMapper.toAdminGameStateDto(state));
        }
        var state = gameEngineService.getGameState(id, principal.getUser().getId());
        return ApiResponse.ok(tenantMapper.toGameStateDto(state));
    }

    @GetMapping("/{id}/fairness")
    @PreAuthorize("hasAnyRole('PLAYER', 'ADMIN')")
    public ApiResponse<?> getFairnessProof(@PathVariable Long id) {
        return ApiResponse.ok(gameService.getFairnessProof(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GameResponse> createGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateGameRequest request) {
        var game = gameService.createGameWithEntryFee(principal.getUser().getId(), request);
        return ApiResponse.ok("Game created", game);
    }

    @PatchMapping("/{id}/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GameResponse> updateGameSettings(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody GameSettingsUpdateRequest request) {
        var game = gameService.updateGameSettings(id, principal.getUser().getId(),
                request.maxPlayers(), request.callInterval(), request.winningPattern(), request.commissionPercent(), request.autoMark());
        return ApiResponse.ok("Game settings updated", game);
    }

    @PostMapping("/{id}/call-next")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> callNextNumber(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var game = gameService.getGameById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        if (!game.adminUserId().equals(principal.getUser().getId())) {
            throw new RuntimeException("Game does not belong to this admin");
        }
        Integer number = gameEngineService.callNumber(id);
        if (number == null) {
            return ApiResponse.ok("No more numbers to call or game is not in progress");
        }
        return ApiResponse.ok("Called number: " + number);
    }

    @PostMapping("/{id}/call/{number}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> callSpecificNumber(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Integer number) {
        var game = gameService.getGameById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        if (!game.adminUserId().equals(principal.getUser().getId())) {
            throw new RuntimeException("Game does not belong to this admin");
        }
        gameEngineService.callSpecificNumber(id, number);
        return ApiResponse.ok("Called number: " + number);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GameResponse> startGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var game = gameService.startGameForAdmin(principal.getUser().getId(), id);
        gameEngineService.scheduleGameStart(game.id(), 5);
        return ApiResponse.ok("Game starting", game);
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
    public ApiResponse<String> pauseGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var game = gameService.getGameById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        if (!game.adminUserId().equals(principal.getUser().getId())) {
            throw new RuntimeException("Game does not belong to this admin");
        }
        gameEngineService.pauseGame(id);
        return ApiResponse.ok("Game paused");
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> resumeGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var game = gameService.getGameById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        if (!game.adminUserId().equals(principal.getUser().getId())) {
            throw new RuntimeException("Game does not belong to this admin");
        }
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
                return ApiResponse.ok(gameService.findOpenGamesForAdmin(user.getId()));
            }
            case PLAYER -> {
                Long adminUserId = AdminIds.adminUserId(user);
                if (adminUserId == null) return ApiResponse.ok(List.of());
                return ApiResponse.ok(gameService.findOpenGamesForAdmin(adminUserId));
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
                .cardId(gameCard.card().id())
                .build());
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<BingoClaimResultResponse> claim(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) ClaimBingoRequest request) throws JsonProcessingException {
        var result = gameEngineService.claimBingo(id, principal.getUser().getId(),
                request == null ? null : request.getMarkedNumbers());
        String message;
        if (result.isBanned()) {
            message = "Invalid Bingo claim — you have been banned from this game";
        } else if (result.isPendingReview()) {
            message = "Bingo claimed! Waiting for admin review.";
        } else {
            message = "Bingo claim processed";
        }
        return ApiResponse.ok(message, BingoClaimResultResponse.builder()
                .valid(result.isValid())
                .claimId(result.getClaimId())
                .pendingReview(result.isPendingReview())
                .rewardAmount(result.getRewardAmount())
                .commission(result.getCommission())
                .banned(result.isBanned())
                .build());
    }

    @GetMapping("/{id}/claims/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<BingoClaimResponse>> getPendingClaims(@PathVariable Long id) {
        return ApiResponse.ok(gameEngineService.getPendingClaims(id));
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

    @PostMapping("/{id}/marks")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<Void> saveMarks(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestBody ClaimBingoRequest request) {
        gameEngineService.saveMarks(id, principal.getUser().getId(), request.getMarkedNumbers());
        return ApiResponse.ok("Marks saved", null);
    }

    @PostMapping("/{id}/claims/approve-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BingoClaimResultResponse> approveAllClaims(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var result = gameEngineService.approveAllClaims(id, principal.getUser().getId());
        return ApiResponse.ok("All pending claims approved — winners share the pot. Game ended.",
                BingoClaimResultResponse.builder()
                        .valid(true)
                        .pendingReview(false)
                        .gameEnded(true)
                        .approvedCount(result.getApprovedCount())
                        .rewardAmount(result.getRewardAmount())
                        .commission(result.getCommission())
                        .build());
    }

    @PostMapping("/{id}/restart")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<GameResponse> restartGame(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        var game = gameService.restartGame(id, principal.getUser().getId());
        gameEngineService.scheduleGameStart(id, 5);
        return ApiResponse.ok("Game restarted with a fresh number sequence", game);
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<GameResponse> audit(@PathVariable Long id) {
        var game = gameService.getGameById(id)
                .orElseThrow(() -> new RuntimeException("Game not found"));
        return ApiResponse.ok(game);
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<GameResponse>> gameHistory(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gameService.getAllGamesForAdmin(principal.getUser().getId()));
    }

    @GetMapping("/player/history")
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<List<GameResponse>> playerGameHistory(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(gameService.getGamesForPlayer(principal.getUser().getId()));
    }
}
