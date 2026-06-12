package com.bingo.app.master.controller;

import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.GameResponse;
import com.bingo.app.tenant.service.GameService;
import com.bingo.app.tenant.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final GameService gameService;
    private final WalletService walletService;
    private final TenantMapper tenantMapper;

    @GetMapping("/revenue")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<Map<String, Object>> revenue(@AuthenticationPrincipal UserPrincipal principal) {
        var user = principal.getUser();
        var report = Map.<String, Object>of(
                "totalGames", gameService.findAllGames().size(),
                "totalTransactions", walletService.getAllTransactions().size(),
                "balance", user.getBalance()
        );
        return ApiResponse.ok(report);
    }

    @GetMapping("/games")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<List<GameResponse>> gameHistory(@AuthenticationPrincipal UserPrincipal principal) {
        var user = principal.getUser();
        var games = switch (user.getRole()) {
            case ADMIN -> gameService.getAllGamesForAdmin(user.getId());
            default -> gameService.findAllGames();
        };
        return ApiResponse.ok(games.stream().map(tenantMapper::toDto).toList());
    }
}
