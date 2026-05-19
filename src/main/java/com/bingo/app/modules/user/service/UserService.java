package com.bingo.app.modules.user.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import com.bingo.app.infrastructure.tenant.TenantContext;
import com.bingo.app.infrastructure.tenant.TenantManagementService;
import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.enums.Role;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import com.bingo.app.modules.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final TenantManagementService tenantManagementService;

    public User createPlayer(Long telegramId, Long parentId) {
        return createUser(telegramId, Role.PLAYER, parentId, BigDecimal.ZERO);
    }

    public Optional<User> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    public User createAdmin(Long telegramId, Long parentId, BigDecimal initialBalance) {
        return createUser(telegramId, Role.ADMIN, parentId, initialBalance);
    }

    @Transactional("masterTransactionManager")
    public User createInvitedUser(Long telegramId, User inviter) {
        if (inviter == null) {
            throw new IllegalStateException("Inviter not found.");
        }

        if (inviter.getRole() == Role.SUPER_ADMIN) {
            User admin = createAdmin(telegramId, inviter.getId(), BigDecimal.ZERO);
            String previousTenant = TenantContext.get();
            TenantContext.set(TenantContext.masterTenant());
            try {
                tenantManagementService.registerTenant(admin.getId());
            } finally {
                TenantContext.set(previousTenant);
            }
            return admin;
        }

        if (inviter.getRole() == Role.ADMIN) {
            return createPlayer(telegramId, inviter.getId());
        }

        if (inviter.getRole() == Role.PLAYER) {
            return createPlayer(telegramId, inviter.getParentId());
        }

        throw new IllegalStateException("Only super admins and admins can invite users.");
    }

    public User requireUser(Long telegramId) {
        return findByTelegramId(telegramId)
                .orElseThrow(() -> new IllegalStateException("User not found with telegramId: " + telegramId));
    }

    public User createSuperAdmin(Long telegramId) {
        return createUser(telegramId, Role.SUPER_ADMIN, null, BigDecimal.ZERO);
    }

    public User ensureSuperAdmin(Long telegramId) {
        Optional<User> userOpt = userRepository.findByTelegramId(telegramId);

        if (userOpt.isPresent()) {
            User existing = userOpt.get();
            existing.setRole(Role.SUPER_ADMIN);
            existing.setParentId(null);
            existing.setActive(true);
            return userRepository.save(existing);
        }

        return createSuperAdmin(telegramId);
    }
    public List<User> findAllByParentIdAndRole(Long parentId, Role role) {
        return userRepository.findByParentIdAndRole(parentId, role);
    }

    public List<User> findAllByRole(Role role) {
        return userRepository.findByRole(role);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByTelegramId(Long.valueOf(username))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with telegramId: " + username));
    }

    private User createUser(Long telegramId, Role role, Long parentId, BigDecimal balance) {
        User user = User.builder()
                .telegramId(telegramId)
                .role(role)
                .parentId(parentId)
                .balance(balance == null ? BigDecimal.ZERO : balance)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return userRepository.save(user);
    }
}
