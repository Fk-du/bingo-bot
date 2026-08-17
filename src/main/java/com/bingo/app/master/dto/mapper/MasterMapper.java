package com.bingo.app.master.dto.mapper;

import com.bingo.app.master.dto.response.*;
import com.bingo.app.master.entity.*;
import org.springframework.stereotype.Component;

@Component
public class MasterMapper {

    public UserProfileResponse toUserProfile(User user) {
        if (user == null) return null;
        return UserProfileResponse.builder()
                .id(user.getId())
                .telegramId(user.getTelegramId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .adminUserId(user.getAdminUserId())
                .businessName(user.getBusinessName())
                .adminApproved(user.isAdminApproved())
                .parentId(user.getParentId())
                .balance(user.getBalance())
                .frozenBalance(user.getFrozenBalance())
                .active(user.isActive())
                .build();
    }

    public AdminListItem toAdminListItem(User user) {
        if (user == null) return null;
        return AdminListItem.builder()
                .adminUserId(user.getId())
                .approved(user.isAdminApproved())
                .businessName(user.getBusinessName())
                .telegramId(user.getTelegramId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .balance(user.getBalance())
                .frozenBalance(user.getFrozenBalance())
                .active(user.isActive())
                .build();
    }

    public AdminFundRequestResponse toDto(AdminFundRequest request) {
        if (request == null) return null;
        return AdminFundRequestResponse.builder()
                .id(request.getId())
                .adminUserId(request.getAdminUserId())
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

    public TenantRegistryResponse toDto(TenantRegistry registry) {
        if (registry == null) return null;
        return TenantRegistryResponse.builder()
                .id(registry.getId())
                .adminUserId(registry.getAdminUserId())
                .databaseName(registry.getDatabaseName())
                .createdAt(registry.getCreatedAt())
                .build();
    }
}
