package com.bingo.app.tenant.controller;

import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.tenant.dto.response.AuditLogResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-log")
public class AuditLogController {

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<AuditLogResponse>> getAuditLog() {
        return ApiResponse.ok(List.of());
    }
}
