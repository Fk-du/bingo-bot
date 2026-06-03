package com.bingo.app.master.controller;

import com.bingo.app.master.dto.request.ConfigUpdateRequest;
import com.bingo.app.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    @GetMapping
    public ApiResponse<Map<String, Object>> getConfig() {
        return ApiResponse.ok(Map.of("status", "configuration endpoint ready"));
    }

    @PatchMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> updateConfig(@Valid @RequestBody ConfigUpdateRequest request) {
        return ApiResponse.ok("Configuration updated");
    }
}
