package com.bingo.app.master.dto.response;

import com.bingo.app.master.entity.User;
import lombok.Builder;

import java.math.BigDecimal;

@Builder(toBuilder = true)
public record UserProfileResponse(
        Long id,
        Long telegramId,
        String username,
        String firstName,
        String lastName,
        String role,
        Long adminUserId,
        String businessName,
        boolean adminApproved,
        Long parentId,
        BigDecimal balance,
        BigDecimal frozenBalance,
        boolean active
) {
    public static UserProfileResponse from(User user) {
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
}
