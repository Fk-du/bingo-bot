package com.bingo.app.modules.game.controller;

import com.bingo.app.modules.config.service.PlatformConfigService;
import com.bingo.app.modules.game.entity.Game;
import com.bingo.app.modules.game.service.GameService;
import com.bingo.app.modules.invite.service.InviteService;
import com.bingo.app.modules.topup.entity.TopUpRequest;
import com.bingo.app.modules.topup.service.TopUpService;
import com.bingo.app.modules.user.enums.Role;
import com.bingo.app.modules.wallet.service.WalletService;
import com.bingo.app.modules.wallet.entity.Transaction;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final UserService userService;
    private final InviteService inviteService;
    private final WalletService walletService;
    private final GameService gameService;
    private final PlatformConfigService platformConfigService;
    private final TopUpService topUpService;

    @PostMapping("/agents/create")
    public ResponseEntity<String> createAgent(@AuthenticationPrincipal User superUser, @RequestParam String botUsername) {
        String inviteLink = inviteService.generateInviteLinkForUser(superUser.getId(), botUsername);
        return ResponseEntity.ok(inviteLink);
    }

    @PostMapping("/agents/{id}/fund")
    public ResponseEntity<Void> fundAgent(@AuthenticationPrincipal User superUser, @PathVariable Long id, @RequestParam BigDecimal amount) {
        walletService.fundAgent(superUser.getId(), id, amount);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/agents")
    public ResponseEntity<List<User>> getAllAgents() {
        return ResponseEntity.ok(userService.findAllByRole(Role.ADMIN));
    }

    @GetMapping("/reports")
    public ResponseEntity<Map<String, Object>> getReports() {
        long totalAgents = userService.findAllByRole(Role.ADMIN).size();
        long totalPlayers = userService.findAllByRole(Role.PLAYER).size();
        long totalGames = gameService.findAllGames().size();
        long totalTransactions = walletService.getAllTransactions().size();

        Map<String, Object> report = Map.of(
                "totalAgents", totalAgents,
                "totalPlayers", totalPlayers,
                "totalGames", totalGames,
                "totalTransactions", totalTransactions
        );

        return ResponseEntity.ok(report);
    }

    @GetMapping("/games/active")
    public ResponseEntity<List<Game>> getActiveGames() {
        var allGames = gameService.findAllGames();
        var activeGames = allGames.stream()
                .filter(g -> g.getStatus() == com.bingo.app.modules.game.enums.GameStatus.REGISTRATION_OPEN
                        || g.getStatus() == com.bingo.app.modules.game.enums.GameStatus.IN_PROGRESS
                        || g.getStatus() == com.bingo.app.modules.game.enums.GameStatus.CLAIM_PENDING)
                .toList();

        return ResponseEntity.ok(activeGames);
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(walletService.getAllTransactions());
    }

    @GetMapping("/withdrawals")
    public ResponseEntity<List<Transaction>> getPendingWithdrawals() {
        return ResponseEntity.ok(walletService.getAllTransactions().stream()
                .filter(tx -> tx.getType() == com.bingo.app.modules.wallet.enums.TransactionType.WITHDRAW_REQUEST)
                .filter(tx -> tx.getStatus() == com.bingo.app.modules.wallet.enums.TransactionStatus.PENDING)
                .toList());
    }

    @PostMapping("/withdrawals/{id}/pay")
    public ResponseEntity<Void> payWithdrawal(@AuthenticationPrincipal User superUser, @PathVariable Long id) {
        walletService.approveWithdrawRequestBySuperAdmin(superUser.getId(), id);
        return ResponseEntity.ok().build();
    }

    // --- Top-Up Requests (from Admins) ---

    @GetMapping("/topup/pending")
    public ResponseEntity<List<TopUpRequest>> getPendingTopUps(@AuthenticationPrincipal User superUser) {
        return ResponseEntity.ok(topUpService.getPendingRequestsForApprover(superUser.getId()));
    }

    @PostMapping("/topup/{id}/approve")
    public ResponseEntity<TopUpRequest> approveTopUp(@AuthenticationPrincipal User superUser, @PathVariable Long id) {
        TopUpRequest request = topUpService.approveRequest(id, superUser.getId());
        return ResponseEntity.ok(request);
    }

    @PostMapping("/topup/{id}/reject")
    public ResponseEntity<TopUpRequest> rejectTopUp(@AuthenticationPrincipal User superUser, @PathVariable Long id) {
        TopUpRequest request = topUpService.rejectRequest(id, superUser.getId());
        return ResponseEntity.ok(request);
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, String>> getSettings() {
        return ResponseEntity.ok(platformConfigService.getAll());
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, String>> updateSettings(@RequestBody Map<String, String> settings) {
        settings.forEach(platformConfigService::set);
        return ResponseEntity.ok(Map.of("status", "Settings updated successfully."));
    }
}
