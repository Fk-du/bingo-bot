package com.bingo.app.master.controller;

import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.master.dto.mapper.MasterMapper;
import com.bingo.app.master.dto.request.AdminStatusRequest;
import com.bingo.app.master.dto.request.CreateAdminFundRequest;
import com.bingo.app.master.dto.response.AdminFundRequestResponse;
import com.bingo.app.master.dto.response.AdminListItem;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.service.InviteService;
import com.bingo.app.master.service.UserService;
import com.bingo.app.tenant.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/admins", "/api/v1/agents"})
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final InviteService inviteService;
    private final WalletService walletService;
    private final MasterMapper masterMapper;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<AdminListItem>> listAdmins() {
        return ApiResponse.ok(userService.findAllByRole(Role.ADMIN));
    }

    @PostMapping("/invite")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> inviteAdmin(@AuthenticationPrincipal UserPrincipal principal) {
        String link = inviteService.generateInviteLinkForUser(principal.getUser().getId(), "BingoPlusBot");
        return ApiResponse.ok("Invite link generated", link);
    }

    @PatchMapping("/{adminUserId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AdminListItem> updateAdminStatus(
            @PathVariable Long adminUserId,
            @Valid @RequestBody AdminStatusRequest request) {
        var admin = switch (request.status().toUpperCase()) {
            case "APPROVE" -> userService.approveAdmin(adminUserId);
            case "REJECT" -> userService.rejectAdmin(adminUserId);
            default -> throw new IllegalArgumentException("Unknown status: " + request.status());
        };
        return ApiResponse.ok("Admin status updated", admin);
    }

    @PostMapping("/fund-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminFundRequestResponse> createFundRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAdminFundRequest request) {
        var fundRequest = walletService.requestAdminFund(
                principal.getUser().getId(), request.amount(), request.screenshotUrl());
        return ApiResponse.ok("Fund request created", masterMapper.toDto(fundRequest));
    }

    @GetMapping("/fund-requests")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<List<AdminFundRequestResponse>> listFundRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        var user = principal.getUser();
        return switch (user.getRole()) {
            case SUPER_ADMIN -> ApiResponse.ok(
                    walletService.getPendingAdminFundRequests().stream()
                            .map(masterMapper::toDto).toList());
            case ADMIN -> ApiResponse.ok(
                    walletService.getAdminFundRequestsByAdmin(user.getId()).stream()
                            .map(masterMapper::toDto).toList());
            default -> ApiResponse.ok(List.of());
        };
    }

    @PatchMapping("/fund-requests/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> handleFundRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        String action = body.getOrDefault("action", "").toUpperCase();
        switch (action) {
            case "APPROVE" -> walletService.approveAdminFundRequest(id, principal.getUser().getId());
            case "REJECT" -> walletService.rejectAdminFundRequest(id, principal.getUser().getId(), body.get("reason"));
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
        return ApiResponse.ok("Fund request " + action.toLowerCase() + "d");
    }
}
