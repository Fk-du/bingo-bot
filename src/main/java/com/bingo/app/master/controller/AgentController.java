package com.bingo.app.master.controller;

import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.master.dto.mapper.MasterMapper;
import com.bingo.app.master.dto.request.AgentStatusRequest;
import com.bingo.app.master.dto.response.AgentFundRequestResponse;
import com.bingo.app.master.dto.response.AgentResponse;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.service.AgentService;
import com.bingo.app.master.service.InviteService;
import com.bingo.app.master.service.UserService;
import com.bingo.app.tenant.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final UserService userService;
    private final AgentService agentService;
    private final InviteService inviteService;
    private final WalletService walletService;
    private final MasterMapper masterMapper;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<UserProfileResponse>> listAgents() {
        var agents = userService.findAllByRole(Role.ADMIN)
                .stream()
                .map(UserProfileResponse::from)
                .toList();
        return ApiResponse.ok(agents);
    }

    @PostMapping("/invite")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> inviteAgent(@AuthenticationPrincipal UserPrincipal principal) {
        String link = inviteService.generateInviteLinkForUser(principal.getUser().getId(), "BingoPlusBot");
        return ApiResponse.ok("Invite link generated", link);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AgentResponse> updateAgentStatus(
            @PathVariable Long id,
            @Valid @RequestBody AgentStatusRequest request) {
        var agent = switch (request.status().toUpperCase()) {
            case "APPROVE" -> agentService.approveAgent(id);
            default -> throw new IllegalArgumentException("Unknown status: " + request.status());
        };
        return ApiResponse.ok("Agent status updated", masterMapper.toDto(agent));
    }

    @PostMapping("/fund-requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AgentFundRequestResponse> createFundRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.getOrDefault("amount", "0").toString());
        String screenshotUrl = (String) body.get("screenshotUrl");
        var request = walletService.requestAgentFund(
                principal.getUser().getId(), amount, screenshotUrl);
        return ApiResponse.ok("Fund request created", masterMapper.toDto(request));
    }

    @GetMapping("/fund-requests")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<List<AgentFundRequestResponse>> listFundRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        var user = principal.getUser();
        return switch (user.getRole()) {
            case SUPER_ADMIN -> ApiResponse.ok(
                    walletService.getPendingAgentFundRequests().stream()
                            .map(masterMapper::toDto).toList());
            case ADMIN -> ApiResponse.ok(
                    walletService.getAgentFundRequestsByAgent(user.getId()).stream()
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
            case "APPROVE" -> walletService.approveAgentFundRequest(id, principal.getUser().getId());
            case "REJECT" -> walletService.rejectAgentFundRequest(id, principal.getUser().getId(), body.get("reason"));
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
        return ApiResponse.ok("Fund request " + action.toLowerCase() + "d");
    }
}
