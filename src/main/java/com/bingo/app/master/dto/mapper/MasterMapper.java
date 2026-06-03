package com.bingo.app.master.dto.mapper;

import com.bingo.app.master.dto.response.*;
import com.bingo.app.master.entity.*;
import com.bingo.app.master.service.InviteService;
import org.springframework.stereotype.Component;

@Component
public class MasterMapper {

    public AgentResponse toDto(Agent agent) {
        if (agent == null) return null;
        return AgentResponse.builder()
                .id(agent.getId())
                .userId(agent.getUserId())
                .businessName(agent.getBusinessName())
                .approved(agent.isApproved())
                .active(agent.isActive())
                .createdAt(agent.getCreatedAt())
                .build();
    }

    public AgentFundRequestResponse toDto(AgentFundRequest request) {
        if (request == null) return null;
        return AgentFundRequestResponse.builder()
                .id(request.getId())
                .agentId(request.getAgentId())
                .amount(request.getAmount())
                .screenshotUrl(request.getScreenshotUrl())
                .status(request.getStatus())
                .approvedBy(request.getApprovedBy())
                .approvedAt(request.getApprovedAt())
                .rejectionReason(request.getRejectionReason())
                .createdAt(request.getCreatedAt())
                .build();
    }

    public InviteCodeResponse toDto(InviteCode inviteCode) {
        if (inviteCode == null) return null;
        return InviteCodeResponse.builder()
                .id(inviteCode.getId())
                .code(inviteCode.getCode())
                .creatorId(inviteCode.getCreatorId())
                .role(inviteCode.getRole())
                .active(inviteCode.isActive())
                .createdAt(inviteCode.getCreatedAt())
                .build();
    }

    public InviteCodeStatsResponse toDto(InviteService.InviteCodeStats stats) {
        if (stats == null) return null;
        return InviteCodeStatsResponse.builder()
                .totalCodes(stats.getTotalCodes())
                .activeCodes(stats.getActiveCodes())
                .usedCodes(stats.getUsedCodes())
                .totalRegistrations(stats.getTotalRegistrations())
                .build();
    }

    public TenantRegistryResponse toDto(TenantRegistry registry) {
        if (registry == null) return null;
        return TenantRegistryResponse.builder()
                .id(registry.getId())
                .agentId(registry.getAgentId())
                .databaseName(registry.getDatabaseName())
                .createdAt(registry.getCreatedAt())
                .build();
    }
}
