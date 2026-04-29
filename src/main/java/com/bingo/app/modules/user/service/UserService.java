package com.bingo.app.modules.user.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import com.bingo.app.modules.user.enums.Role;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import com.bingo.app.modules.user.entity.User;
import com.bingo.app.modules.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    public User createPlayer(Long telegramId, Long parentId) {
        User user = User.builder()
                .telegramId(telegramId)
                .role(Role.PLAYER)
                .parentId(parentId)
                .balance(BigDecimal.ZERO)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return userRepository.save(user);
    }

    public Optional<User> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    public User createAdmin(Long telegramId, Long parentId, BigDecimal initialBalance) {

        User admin = User.builder()
                .telegramId(telegramId)
                .role(Role.ADMIN)
                .parentId(parentId)
                .balance(initialBalance)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return userRepository.save(admin);
    }


    public User createSuperAdmin(Long telegramId) {
        User superAdmin = User.builder()
                .telegramId(telegramId)
                .role(Role.SUPER_ADMIN)
                .parentId(null)
                .balance(BigDecimal.ZERO)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        return userRepository.save(superAdmin);
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
}
