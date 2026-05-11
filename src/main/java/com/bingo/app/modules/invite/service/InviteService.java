package com.bingo.app.modules.invite.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.bingo.app.modules.user.enums.Role;
import com.bingo.app.exception.InviteRegistrationException;
import com.bingo.app.modules.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.bingo.app.modules.invite.entity.InviteCode;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.invite.repository.InviteCodeRepository;
import com.bingo.app.modules.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class InviteService {

    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @Transactional
    public User registerWithInvite(Long telegramId, String code) {
        userService.findByTelegramId(telegramId).ifPresent(user -> {
            throw InviteRegistrationException.alreadyRegistered();
        });

        InviteCode invite = inviteCodeRepository.findByCode(code)
                .orElseThrow(InviteRegistrationException::invalidCode);

        if (!invite.isActive()) {
            throw InviteRegistrationException.inactiveCode();
        }

        User parentUser = userRepository.findById(invite.getAdminId())
                .orElseThrow(InviteRegistrationException::inviterNotFound);

        if (parentUser.getRole() == Role.PLAYER) {
            throw InviteRegistrationException.invalidInviterRole();
        }

        User createdUser = userService.createInvitedUser(telegramId, parentUser);
        invite.setActive(false);
        inviteCodeRepository.save(invite);

        return createdUser;
    }

    public String generateInviteLinkForUser(Long inviterUserId, String botUsername) {
        User inviter = userRepository.findById(inviterUserId)
                .orElseThrow(InviteRegistrationException::inviterNotFound);

        Long effectiveParentId = inviter.getRole() == Role.PLAYER
                ? inviter.getParentId()
                : inviter.getId();

        if (effectiveParentId == null) {
            throw InviteRegistrationException.invalidInviterRole();
        }

        User effectiveParent = userRepository.findById(effectiveParentId)
                .orElseThrow(InviteRegistrationException::inviterNotFound);

        if (effectiveParent.getRole() == Role.PLAYER) {
            throw InviteRegistrationException.invalidInviterRole();
        }

        String code = buildUniqueCode();

        InviteCode invite = InviteCode.builder()
                .code(code)
                .adminId(effectiveParent.getId())
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        inviteCodeRepository.save(invite);

        return "https://t.me/" + botUsername + "?start=" + code;
    }

    private String buildUniqueCode() {
        String code;

        do {
            code = "agent_" + UUID.randomUUID().toString().substring(0, 6);
        } while (inviteCodeRepository.existsByCode(code));

        return code;
    }
}
