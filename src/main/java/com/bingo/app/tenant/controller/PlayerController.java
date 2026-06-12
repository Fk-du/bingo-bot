package com.bingo.app.tenant.controller;

import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.tenant.dto.request.PlayerStatusRequest;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.PlayerResponse;
import com.bingo.app.tenant.service.PlayerService;
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
    private final TenantMapper tenantMapper;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<PlayerResponse>> listPlayers(@AuthenticationPrincipal UserPrincipal principal) {
        var players = playerService.getPlayersByAdmin(principal.getUser().getId())
                .stream()
                .map(tenantMapper::toDto)
                .toList();
        return ApiResponse.ok(players);
    }

//    @PatchMapping("/{id}/status")
//    @PreAuthorize("hasRole('ADMIN')")
//    public ApiResponse<String> updatePlayerStatus(
//            @PathVariable Long id,
//            @Valid @RequestBody PlayerStatusRequest request) {
//        return ApiResponse.ok("Player status update: " + request.status());
//    }
}
