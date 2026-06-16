package com.bingo.app.master.controller;

import com.bingo.app.master.dto.request.ConfigUpdateRequest;
import com.bingo.app.master.service.ConfigService;
import com.bingo.app.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @GetMapping
    public ApiResponse<Map<String, Object>> getConfig() {
        return ApiResponse.ok(configService.getAll());
    }

    @PatchMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> updateConfig(@Valid @RequestBody ConfigUpdateRequest request) {
        configService.updateAll(request.config());
        return ApiResponse.ok("Configuration updated");
    }
}
