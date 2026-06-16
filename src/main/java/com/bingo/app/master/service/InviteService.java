package com.bingo.app.master.service;

import com.bingo.app.infrastructure.persistence.TenantContext;
import com.bingo.app.infrastructure.persistence.TenantManagementService;
import com.bingo.app.master.dto.request.CreateAdminRequest;
import com.bingo.app.master.dto.request.CreatePlayerRequest;
import com.bingo.app.master.entity.InviteCode;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.InviteCodeRepository;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.service.CardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InviteService {

    private final InviteCodeRepository inviteCodeRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final TenantManagementService tenantManagementService;
    private final CardService cardService;

    /**
     * Generate an invite link for a user
     */
    @Transactional
    public String generateInviteLinkForUser(Long creatorId, String botUsername) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Role targetRole = creator.getRole() == Role.SUPER_ADMIN ? Role.ADMIN : Role.PLAYER;

        // Admin player invites are permanent — reuse existing active code if any
        if (targetRole == Role.PLAYER) {
            List<InviteCode> existing = inviteCodeRepository.findByCreatorIdAndActiveTrue(creatorId);
            if (!existing.isEmpty()) {
                String code = existing.get(0).getCode();
                return "https://t.me/" + botUsername + "?start=" + code;
            }
        }

        String code = generateUniqueCode();

        InviteCode inviteCode = InviteCode.builder()
                .code(code)
                .creatorId(creatorId)
                .role(targetRole)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        inviteCodeRepository.save(inviteCode);

        return "https://t.me/" + botUsername + "?start=" + code;
    }

    /**
     * Register a user with an invite code
     *
     * NOT @Transactional — CREATE DATABASE inside the chain cannot run in a transaction.
     * Individual JpaRepository calls handle their own implicit transactions.
     */
    public User registerWithInvite(Long telegramId, String code) {
        log.info("Registering user telegramId={} with code={}", telegramId, code);

        // Validate invite code
        InviteCode inviteCode = inviteCodeRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new RuntimeException("Invalid or expired invite code"));

        // Check if user already exists
        if (userRepository.existsByTelegramId(telegramId)) {
            throw new RuntimeException("User already registered");
        }

        // Get creator info
        User creator = userRepository.findById(inviteCode.getCreatorId())
                .orElseThrow(() -> new RuntimeException("Invalid invite code creator"));

        // Get Telegram user info (will be updated from Telegram later)
        String username = "user_" + telegramId;
        String firstName = "User";
        String lastName = "";

        User newUser;

        if (inviteCode.getRole() == Role.ADMIN) {
            // Create new admin (also creates tenant database)
            newUser = userService.createAdmin(new CreateAdminRequest(
                    creator.getId(),
                    telegramId,
                    username,
                    firstName,
                    lastName
            ));

            log.info("New admin registered: id={}, telegramId={}", newUser.getId(), telegramId);

        } else {
            // Create new player
            Long adminUserId = creator.getRole() == Role.ADMIN ? creator.getId() : creator.getAdminUserId();

            if (adminUserId == null) {
                throw new RuntimeException("Cannot create player: no admin assigned");
            }

            tenantManagementService.createTenant(adminUserId);

            newUser = userService.createPlayer(new CreatePlayerRequest(
                    adminUserId,
                    telegramId,
                    username,
                    firstName,
                    lastName,
                    adminUserId
            ));

            String tenant = TenantContext.tenantKeyForAdmin(adminUserId);
            TenantContext.setTenant(tenant);
            try {
                cardService.assignNewCardToPlayer(newUser.getId());
                log.info("Default card assigned to new player: {}", newUser.getId());
            } catch (Exception e) {
                log.warn("Failed to assign default card to player {}: {}", newUser.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }

            log.info("New player registered: id={}, telegramId={}, adminUserId={}",
                    newUser.getId(), telegramId, adminUserId);
        }

        // Deactivate ADMIN invite codes after use (single-use per admin).
        // PLAYER invite codes remain active forever so admins can share a permanent link.
        if (inviteCode.getRole() == Role.ADMIN) {
            inviteCode.setActive(false);
            inviteCodeRepository.save(inviteCode);
        }

        return newUser;
    }

    /**
     * Validate an invite code
     */
    public InviteCode validateInviteCode(String code) {
        return inviteCodeRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new RuntimeException("Invalid or expired invite code"));
    }

    /**
     * Generate a unique invite code
     */
    private String generateUniqueCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (inviteCodeRepository.existsByCode(code));
        return code;
    }

    /**
     * Deactivate an invite code
     */
    @Transactional
    public void deactivateInviteCode(String code) {
        inviteCodeRepository.deactivateInviteCode(code);
        log.info("Deactivated invite code: {}", code);
    }

    /**
     * Get all active invite codes for a creator
     */
    public List<InviteCode> getActiveInviteCodesForCreator(Long creatorId) {
        return inviteCodeRepository.findByCreatorIdAndActiveTrue(creatorId);
    }

    /**
     * Get invite code statistics
     */
    public InviteCodeStats getInviteCodeStats(Long creatorId) {
        List<InviteCode> codes = inviteCodeRepository.findByCreatorId(creatorId);
        long totalCodes = codes.size();
        long usedCodes = codes.stream().filter(c -> !c.isActive()).count();
        long activeCodes = totalCodes - usedCodes;

        // Count registrations from this creator's invites
        long registrations = 0;
        if (creatorId != null) {
            User creator = userRepository.findById(creatorId).orElse(null);
            if (creator != null && creator.getRole() == Role.ADMIN) {
                registrations = userRepository.countByParentId(creatorId);
            } else if (creator != null && creator.getRole() == Role.SUPER_ADMIN) {
                registrations = userRepository.countByRole(Role.ADMIN);
            }
        }

        return InviteCodeStats.builder()
                .totalCodes(totalCodes)
                .activeCodes(activeCodes)
                .usedCodes(usedCodes)
                .totalRegistrations(registrations)
                .build();
    }

    @lombok.Builder
    @lombok.Data
    public static class InviteCodeStats {
        private long totalCodes;
        private long activeCodes;
        private long usedCodes;
        private long totalRegistrations;
    }
}