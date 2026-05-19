package com.bingo.app.modules.game.controller;

import com.bingo.app.modules.game.dto.CreateGameRequest;
import com.bingo.app.modules.game.dto.GameCardResponse;
import com.bingo.app.modules.game.dto.GameResponse;
import com.bingo.app.modules.game.service.CardService;
import com.bingo.app.modules.game.service.GameEngineService;
import com.bingo.app.modules.game.service.GameService;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.enums.Role;
import com.bingo.app.modules.user.service.UserService;
import jakarta.validation.Valid;
import com.bingo.app.modules.topup.entity.TopUpRequest;
import com.bingo.app.modules.topup.service.TopUpService;
import com.bingo.app.modules.wallet.entity.Transaction;
import com.bingo.app.modules.wallet.service.WalletService;
import com.bingo.app.modules.invite.service.InviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final GameService gameService;
    private final GameEngineService gameEngineService;
    private final CardService cardService;
    private final UserService userService;
    private final WalletService walletService;
    private final InviteService inviteService;
    private final TopUpService topUpService;

    // --- Game Management ---

    @PostMapping("/games/create")
    public ResponseEntity<GameResponse> createGame(@AuthenticationPrincipal User admin, @Valid @RequestBody CreateGameRequest request) {
        return ResponseEntity.ok(gameService.createGameWithEntryFee(admin.getId(), request));
    }

    @PostMapping("/games/{id}/start")
    public ResponseEntity<GameResponse> startGame(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        GameResponse game = gameService.startGameForAdmin(admin.getId(), id);
        gameEngineService.startCalling(id);
        return ResponseEntity.ok(game);
    }

    @PostMapping("/games/{id}/pause")
    public ResponseEntity<Void> pauseGame(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        gameService.requireAdminGame(admin.getId(), id);
        gameEngineService.pauseCalling(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/games/{id}/resume")
    public ResponseEntity<Void> resumeGame(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        gameService.requireAdminGame(admin.getId(), id);
        gameEngineService.resumeCalling(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/games/{id}/end")
    public ResponseEntity<GameResponse> endGame(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        GameResponse game = gameService.endGameForAdmin(admin.getId(), id);
        gameEngineService.stopCalling(id);
        return ResponseEntity.ok(game);
    }

    @PostMapping("/games/{id}/reject-player")
    public ResponseEntity<Void> rejectPlayer(@AuthenticationPrincipal User admin, @PathVariable Long id, @RequestParam Long playerId) {
        gameService.requireAdminGame(admin.getId(), id);
        cardService.removePlayerFromGame(id, playerId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/games/status")
    public ResponseEntity<GameResponse> getGameStatus(@AuthenticationPrincipal User admin, @RequestParam Long gameId) {
        return ResponseEntity.ok(GameResponse.from(gameService.requireAdminGame(admin.getId(), gameId)));
    }

    @GetMapping("/game/current")
    public ResponseEntity<GameResponse> getCurrentGame(@AuthenticationPrincipal User admin) {
        return gameService.findCurrentGameForAdmin(admin.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // --- Player Management ---

    @GetMapping("/players")
    public ResponseEntity<List<User>> getMyPlayers(@AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(userService.findAllByParentIdAndRole(admin.getId(), Role.PLAYER));
    }

    @PostMapping("/players/{id}/fund")
    public ResponseEntity<Void> fundPlayer(@AuthenticationPrincipal User admin, @PathVariable Long id, @RequestParam BigDecimal amount) {
        walletService.fundPlayer(admin.getId(), id, amount);
        return ResponseEntity.ok().build();
    }

    // --- Wallet & Withdrawals ---

    @GetMapping("/withdrawals")
    public ResponseEntity<List<Transaction>> getWithdrawalRequests(@AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(walletService.getPendingWithdrawsForAdminPlayers(admin.getId()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getTransactions(@AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(walletService.getTransactionsForAdminPlayers(admin.getId()));
    }

    @PostMapping("/withdrawals/{id}/approve")
    public ResponseEntity<Void> approveWithdrawal(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        walletService.approveWithdrawRequest(admin.getId(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/withdrawals/{id}/reject")
    public ResponseEntity<Void> rejectWithdrawal(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        walletService.rejectWithdrawRequest(admin.getId(), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/points/request")
    public ResponseEntity<Transaction> requestPoints(@AuthenticationPrincipal User admin, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(walletService.createAgentPointRequest(admin.getId(), amount));
    }

    // --- Top-Up Requests (to Super Admin) ---

    @PostMapping("/topup/request")
    public ResponseEntity<TopUpRequest> requestTopUp(@AuthenticationPrincipal User admin, @RequestParam BigDecimal amount, @RequestParam(required = false) String proofImageFileId) {
        if (admin.getParentId() == null) {
            throw new com.bingo.app.exception.PlayerActionException("No super admin", "You are not linked to any super admin.");
        }
        TopUpRequest request = topUpService.createRequest(admin.getId(), admin.getParentId(), amount, proofImageFileId);
        return ResponseEntity.ok(request);
    }

    // --- Top-Up Requests (from Players) ---

    @GetMapping("/topup/pending")
    public ResponseEntity<List<TopUpRequest>> getPendingTopUps(@AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(topUpService.getPendingRequestsForApprover(admin.getId()));
    }

    @PostMapping("/topup/{id}/approve")
    public ResponseEntity<TopUpRequest> approveTopUp(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        TopUpRequest request = topUpService.approveRequest(id, admin.getId());
        return ResponseEntity.ok(request);
    }

    @PostMapping("/topup/{id}/reject")
    public ResponseEntity<TopUpRequest> rejectTopUp(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        TopUpRequest request = topUpService.rejectRequest(id, admin.getId());
        return ResponseEntity.ok(request);
    }

    // --- Invitation ---

    @GetMapping("/invite-link")
    public ResponseEntity<String> getInviteLink(@AuthenticationPrincipal User admin, @RequestParam String botUsername) {
        return ResponseEntity.ok(inviteService.generateInviteLinkForUser(admin.getId(), botUsername));
    }
}
