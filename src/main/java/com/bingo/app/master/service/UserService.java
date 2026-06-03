package com.bingo.app.master.service;

import com.bingo.app.infrastructure.persistence.TenantContext;
import com.bingo.app.infrastructure.persistence.TenantManagementService;
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
    private final AgentService agentService;
    private final PlayerService playerService;

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
    // userRepository.save() handles its own implicit transaction.
    public User createAgent(Long creatorId, Long telegramId, String username, String firstName, String lastName) {
        User agent = User.builder()
                .telegramId(telegramId)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .role(Role.ADMIN)
                .parentId(creatorId)
                .active(true)
                .build();

        User saved = userRepository.save(agent);

        // Create tenant database for this agent
        tenantManagementService.createTenant(saved.getId());

        // Register agent record
        agentService.registerAgent(saved.getId(), null);

        return saved;
    }

    public User createPlayer(Long agentId, Long telegramId, String username, String firstName, String lastName, Long parentId) {
        User player = User.builder()
                .telegramId(telegramId)
                .username(username)
                .firstName(firstName)
                .lastName(lastName)
                .role(Role.PLAYER)
                .agentId(agentId)
                .parentId(parentId)
                .active(true)
                .build();

        User saved = userRepository.save(player);

        String tenant = TenantContext.getAgentTenant(agentId);
        TenantContext.setTenant(tenant);
        try {
            playerService.createPlayer(saved.getId(), agentId, parentId);
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
    public List<User> getPlayersByAgent(Long agentId) {
        return userRepository.findAllByAgentId(agentId);
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public void deductBalance(Long userId, BigDecimal amount) {
        int updated = userRepository.deductBalance(userId, amount);
        if (updated == 0) {
            throw new RuntimeException("Insufficient balance");
        }
    }

    @Transactional(readOnly = true)
    public List<User> findAllByRole(Role role) {
        return userRepository.findAllByRole(role);
    }

    @Transactional(readOnly = true)
    public List<User> findAllByParentIdAndRole(Long parentId, Role role) {
        return userRepository.findAllByParentIdAndRole(parentId, role);
    }

}