package com.bingo.app.modules.game.controller;

import com.bingo.app.modules.invite.service.InviteService;
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

@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {

    private final UserService userService;
    private final InviteService inviteService;
    private final WalletService walletService;
    // private final ReportService reportService; // To be created

    @PostMapping("/agents/create")
    public ResponseEntity<String> createAgent(@AuthenticationPrincipal User superUser, @RequestParam String botUsername) {
        // Logic to generate an invitation code for a new agent
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
    public ResponseEntity<Object> getReports() {
        // Implementation for reports
        return ResponseEntity.ok().build();
    }

    @GetMapping("/games/active")
    public ResponseEntity<List<Object>> getActiveGames() {
        // Implementation for active games
        return ResponseEntity.ok().build();
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Transaction>> getAllTransactions() {
        return ResponseEntity.ok(walletService.getAllTransactions());
    }

    @PutMapping("/settings")
    public ResponseEntity<Void> updateSettings(@RequestBody Object settings) {
        // Implementation for system settings
        return ResponseEntity.ok().build();
    }
}
