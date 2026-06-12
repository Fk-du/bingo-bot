package com.bingo.app.tenant.controller;

import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.tenant.dto.request.CreateWithdrawalRequest;
import com.bingo.app.tenant.dto.request.WithdrawalPayRequest;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.WithdrawalResponse;
import com.bingo.app.tenant.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
public class WithdrawalController {

    private final WalletService walletService;
    private final TenantMapper tenantMapper;

    @PostMapping
    @PreAuthorize("hasRole('PLAYER')")
    public ApiResponse<WithdrawalResponse> createWithdrawal(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateWithdrawalRequest request) {
        var withdrawal = walletService.createWithdrawRequest(
                principal.getUser().getId(), request.amount(), request.payoutDetails());
        return ApiResponse.ok("Withdrawal request created", tenantMapper.toDto(withdrawal));
    }

    @GetMapping
    public ApiResponse<List<WithdrawalResponse>> listWithdrawals(@AuthenticationPrincipal UserPrincipal principal) {
        var user = principal.getUser();
        switch (user.getRole()) {
            case ADMIN -> {
                var requests = walletService.getPendingWithdrawsForAdminPlayers(user.getId())
                        .stream()
                        .map(tenantMapper::toDto)
                        .toList();
                return ApiResponse.ok(requests);
            }
            default -> {
                return ApiResponse.ok(List.of());
            }
        }
    }

    @PatchMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<String> payWithdrawal(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody WithdrawalPayRequest request) {
        walletService.approveWithdrawal(id, principal.getUser().getId());
        return ApiResponse.ok("Withdrawal marked as paid");
    }
}
