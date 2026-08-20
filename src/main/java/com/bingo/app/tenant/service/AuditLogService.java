package com.bingo.app.tenant.service;

import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.AuditLogResponse;
import com.bingo.app.tenant.entity.AuditLog;
import com.bingo.app.tenant.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final TenantMapper tenantMapper;

    @Transactional
    public void record(Long userId, String action, String details, String ipAddress) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .details(details)
                    .ipAddress(ipAddress)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("Failed to write audit log: action={}, userId={}, error={}", action, userId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getByUserId(Long userId) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getByAction(String action) {
        return auditLogRepository.findByActionOrderByCreatedAtDesc(action).stream()
                .map(tenantMapper::toDto)
                .toList();
    }
}
