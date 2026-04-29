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

        User createdUser;

        if (parentUser.getRole() == Role.SUPER_ADMIN) {
            createdUser = userService.createAdmin(telegramId, parentUser.getId(), BigDecimal.ZERO);
        } else if (parentUser.getRole() == Role.ADMIN) {
            createdUser = userService.createPlayer(telegramId, parentUser.getId());
        } else {
            throw InviteRegistrationException.invalidInviterRole();
        }

        invite.setActive(false);
        inviteCodeRepository.save(invite);

        return createdUser;
    }

    public String generateInviteLink(Long adminId, String botUsername) {
        String code = buildUniqueCode();

        InviteCode invite = InviteCode.builder()
                .code(code)
                .adminId(adminId)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        inviteCodeRepository.save(invite);

        return "https://t.me/" + botUsername + "?start=" + code;
    }

    public String createAdminWithInvite(Long telegramId, Long parentId, String botUsername) {
        User admin = userService.createAdmin(telegramId, parentId, BigDecimal.ZERO);

        return generateInviteLink(admin.getId(), botUsername);
    }

    private String buildUniqueCode() {
        String code;

        do {
            code = "agent_" + UUID.randomUUID().toString().substring(0, 6);
        } while (inviteCodeRepository.existsByCode(code));

        return code;
    }
}
