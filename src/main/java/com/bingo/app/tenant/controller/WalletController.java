package com.bingo.app.tenant.controller;

import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.WalletResponse;
import com.bingo.app.tenant.service.PlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final PlayerService playerService;
    private final UserRepository userRepository;
    private final TenantMapper tenantMapper;

    @GetMapping
    public ApiResponse<WalletResponse> getWallet(@AuthenticationPrincipal UserPrincipal principal) {
        var user = principal.getUser();
        if (user.getRole() == Role.PLAYER) {
            var player = playerService.findPlayerByUserId(user.getId());
            return ApiResponse.ok(new WalletResponse(player.getBalance(), player.getFrozenBalance()));
        }
        var fresh = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ApiResponse.ok(tenantMapper.toWalletDto(fresh));
    }
}
