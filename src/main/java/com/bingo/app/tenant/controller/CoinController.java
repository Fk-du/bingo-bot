package com.bingo.app.tenant.controller;

import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.tenant.dto.request.BuyPointsRequest;
import com.bingo.app.tenant.dto.request.CoinRequestAction;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.CoinRequestResponse;
import com.bingo.app.tenant.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/coins")
@RequiredArgsConstructor
public class CoinController {

    private final WalletService walletService;
    private final TenantMapper tenantMapper;

    @PostMapping("/requests")
    public ApiResponse<CoinRequestResponse> createRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BuyPointsRequest request) {
        var coinRequest = walletService.buyPoints(principal.getUser().getId(), request.amount(), request.screenshotUrl());
        return ApiResponse.ok("Coin request created", tenantMapper.toDto(coinRequest));
    }

    @GetMapping("/requests")
    public ApiResponse<List<CoinRequestResponse>> listRequests(@AuthenticationPrincipal UserPrincipal principal) {
        var user = principal.getUser();
        switch (user.getRole()) {
            case ADMIN -> {
                var requests = walletService.getPendingCoinRequestsForAdmin(user.getId())
                        .stream()
                        .map(tenantMapper::toDto)
                        .toList();
                return ApiResponse.ok(requests);
            }
            case SUPER_ADMIN -> {
                return ApiResponse.ok(List.of());
            }
            default -> {
                return ApiResponse.ok(List.of());
            }
        }
    }

    @PatchMapping("/requests/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<String> handleRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CoinRequestAction action) {
        Long approverId = principal.getUser().getId();
        switch (action.action().toUpperCase()) {
            case "APPROVE" -> walletService.approveCoinRequest(id, approverId);
            case "REJECT" -> walletService.rejectCoinRequest(id, approverId, action.reason());
            default -> throw new IllegalArgumentException("Unknown action: " + action.action());
        }
        return ApiResponse.ok("Request " + action.action().toLowerCase() + "d");
    }
}
