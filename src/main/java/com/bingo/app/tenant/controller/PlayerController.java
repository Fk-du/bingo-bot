package com.bingo.app.tenant.controller;

import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.tenant.dto.request.FundPlayerRequest;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.tenant.dto.response.PlayerResponse;
import com.bingo.app.tenant.dto.response.WalletResponse;
import com.bingo.app.tenant.service.PlayerService;
import com.bingo.app.tenant.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;
    private final WalletService walletService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<PlayerResponse>> listPlayers(@AuthenticationPrincipal UserPrincipal principal) {
        var players = playerService.getPlayersByAdmin(principal.getUser().getId());
        return ApiResponse.ok(players);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PlayerResponse> getPlayer(@PathVariable Long id) {
        return ApiResponse.ok(playerService.findByUserId(id));
    }

    @PostMapping("/{id}/fund")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> fundPlayer(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FundPlayerRequest request) {
        walletService.fundPlayer(principal.getUser().getId(), id, request.amount());
        return ApiResponse.ok("Player funded successfully");
    }

    @GetMapping("/{id}/wallet")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<WalletResponse> getPlayerWallet(@PathVariable Long id) {
        PlayerResponse player = playerService.findByUserId(id);
        return ApiResponse.ok(WalletResponse.builder()
                .balance(player.balance())
                .frozenBalance(player.frozenBalance())
                .build());
    }
}
