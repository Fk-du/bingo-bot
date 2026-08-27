package com.bingo.app.master.controller;

import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.master.dto.response.InviteCodeStatsResponse;
import com.bingo.app.master.service.InviteService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invite")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @Value("${BOT_USERNAME:lucky_winners_bingo_bot}")
    private String botUsername;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLAYER')")
    public ApiResponse<String> getMyInviteLink(@AuthenticationPrincipal UserPrincipal principal) {
        String link = inviteService.generateInviteLinkForUser(principal.getUser().getId(), botUsername);
        return ApiResponse.ok("Invite link generated", link);
    }

    @GetMapping("/me/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLAYER')")
    public ApiResponse<InviteCodeStatsResponse> getMyInviteStats(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(inviteService.getInviteCodeStats(principal.getUser().getId()));
    }
}
