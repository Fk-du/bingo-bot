package com.bingo.app.master.service;

import com.bingo.app.infrastructure.persistence.TenantContext;
import com.bingo.app.infrastructure.persistence.TenantManagementService;
import com.bingo.app.master.dto.mapper.MasterMapper;
import com.bingo.app.master.dto.request.CreateAdminRequest;
import com.bingo.app.master.dto.request.CreatePlayerRequest;
import com.bingo.app.master.dto.response.AdminListItem;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.tenant.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final TenantManagementService tenantManagementService;
    private final PlayerService playerService;
    private final MasterMapper masterMapper;

    @Value("${app.super-admin.telegram-id}")
    private Long superAdminTelegramId;

    @Transactional
    public User findOrCreateUser(Long telegramId, String username, String firstName, String lastName) {
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> createNewUser(telegramId, username, firstName, lastName));
    }

    private User createNewUser(Long telegramId, String username, String firstName, String lastName) {
        Role role = telegramId.equals(superAdminTelegramId) ? Role.SUPER_ADMIN : Role.PLAYER;

        User user = User.builder()
                .telegramId(telegramId)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .active(true)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public User ensureSuperAdmin(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    User superAdmin = User.builder()
                            .telegramId(telegramId)
                            .username("superadmin")
                            .firstName("Super")
                            .lastName("Admin")
                            .role(Role.SUPER_ADMIN)
                            .active(true)
                            .build();
                    return userRepository.save(superAdmin);
                });
    }

    // NOT @Transactional — createTenant() runs CREATE DATABASE which cannot be in a transaction.
    public User createAdmin(CreateAdminRequest request) {
        User admin = User.builder()
                .telegramId(request.telegramId())
                .username(request.username())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.ADMIN)
                .parentId(request.creatorId())
                .adminApproved(false)
                .active(true)
                .build();

        User saved = userRepository.save(admin);
        tenantManagementService.createTenant(saved.getId());
        return saved;
    }

    @Transactional
    public AdminListItem approveAdmin(Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin not found: " + adminUserId));
        if (admin.getRole() != Role.ADMIN) {
            throw new RuntimeException("User is not an admin: " + adminUserId);
        }
        admin.setAdminApproved(true);
        admin.setActive(true);
        return masterMapper.toAdminListItem(userRepository.save(admin));
    }

    @Transactional
    public AdminListItem rejectAdmin(Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new RuntimeException("Admin not found: " + adminUserId));
        if (admin.getRole() != Role.ADMIN) {
            throw new RuntimeException("User is not an admin: " + adminUserId);
        }
        admin.setAdminApproved(false);
        admin.setActive(false);
        return masterMapper.toAdminListItem(userRepository.save(admin));
    }

    public User createPlayer(CreatePlayerRequest request) {
        User player = User.builder()
                .telegramId(request.telegramId())
                .username(request.username())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.PLAYER)
                .adminUserId(request.adminUserId())
                .parentId(request.parentId())
                .active(true)
                .build();

        User saved = userRepository.save(player);

        String tenant = TenantContext.tenantKeyForAdmin(request.adminUserId());
        TenantContext.setTenant(tenant);
        try {
            playerService.createPlayer(saved.getId(), request.adminUserId(), request.parentId());
            log.info("Player record created in tenant DB for user: {}", saved.getId());
        } finally {
            TenantContext.clear();
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public User findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<UserProfileResponse> getPlayersByAdmin(Long adminUserId) {
        return userRepository.findAllByAdminUserId(adminUserId).stream()
                .map(masterMapper::toUserProfile)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return masterMapper.toUserProfile(user);
    }

    @Transactional
    public void deductBalance(Long userId, BigDecimal amount) {
        int updated = userRepository.deductBalance(userId, amount);
        if (updated == 0) {
            throw new RuntimeException("Insufficient balance");
        }
    }

    @Transactional(readOnly = true)
    public List<AdminListItem> findAllByRole(Role role) {
        return userRepository.findAllByRole(role).stream()
                .map(masterMapper::toAdminListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminListItem> findAllByParentIdAndRole(Long parentId, Role role) {
        return userRepository.findAllByParentIdAndRole(parentId, role).stream()
                .map(masterMapper::toAdminListItem)
                .toList();
    }
}
