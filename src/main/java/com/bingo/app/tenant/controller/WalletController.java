package com.bingo.app.tenant.controller;

import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.infrastructure.persistence.TenantHelper;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.WalletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final UserRepository userRepository;
    private final TenantMapper tenantMapper;

    @GetMapping
    public ApiResponse<WalletResponse> getWallet(@AuthenticationPrincipal UserPrincipal principal) {
        var user = userRepository.findById(principal.getUser().getId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ApiResponse.ok(tenantMapper.toWalletDto(user));
    }
}
