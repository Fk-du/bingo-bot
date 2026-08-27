package com.bingo.app.tenant.service;

import com.bingo.app.tenant.dto.mapper.TenantMapper;
import com.bingo.app.tenant.dto.response.PlayerResponse;
import com.bingo.app.tenant.entity.Player;
import com.bingo.app.tenant.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final TenantMapper tenantMapper;
    private final com.bingo.app.master.repository.UserRepository masterUserRepository;

    @Transactional(transactionManager = "tenantTransactionManager")
    public PlayerResponse createPlayer(Long userId, Long adminUserId, Long parentId) {
        Player player = Player.builder()
                .userId(userId)
                .adminUserId(adminUserId)
                .parentId(parentId)
                .balance(BigDecimal.ZERO)
                .frozenBalance(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();
        return tenantMapper.toDto(playerRepository.save(player));
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public PlayerResponse findByUserId(Long userId) {
        return tenantMapper.toDto(findPlayerEntityByUserId(userId));
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Optional<PlayerResponse> findByUserIdOrNull(Long userId) {
        return Optional.ofNullable(tenantMapper.toDto(findPlayerEntityOrNullByUserId(userId)));
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<PlayerResponse> getPlayersByAdmin(Long adminUserId) {
        return playerRepository.findByAdminUserId(adminUserId).stream()
                .map(player -> withNames(tenantMapper.toDto(player)))
                .toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<PlayerResponse> getPlayersByParent(Long parentId) {
        return playerRepository.findByParentId(parentId).stream()
                .map(tenantMapper::toDto)
                .toList();
    }

    private PlayerResponse withNames(PlayerResponse dto) {
        if (dto == null || dto.userId() == null) return dto;
        return masterUserRepository.findById(dto.userId())
                .map(user -> PlayerResponse.builder()
                        .id(dto.id())
                        .userId(dto.userId())
                        .adminUserId(dto.adminUserId())
                        .parentId(dto.parentId())
                        .balance(dto.balance())
                        .frozenBalance(dto.frozenBalance())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .username(user.getUsername())
                        .createdAt(dto.createdAt())
                        .build())
                .orElse(dto);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public long countByAdminUserId(Long adminUserId) {
        return playerRepository.countByAdminUserId(adminUserId);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public long countByParentId(Long parentId) {
        return playerRepository.countByParentId(parentId);
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public List<Long> getPlayerIdsByAdmin(Long adminUserId) {
        return playerRepository.findByAdminUserId(adminUserId).stream()
                .map(Player::getUserId)
                .toList();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public BigDecimal getBalance(Long userId) {
        return findPlayerEntityByUserId(userId).getBalance();
    }

    @Transactional(transactionManager = "tenantTransactionManager", readOnly = true)
    public Player findPlayerByUserId(Long userId) {
        return findPlayerEntityByUserId(userId);
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void deductBalance(Long userId, BigDecimal amount) {
        int updated = playerRepository.deductBalance(userId, amount);
        if (updated == 0) {
            throw new RuntimeException("Insufficient balance");
        }
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void addBalance(Long userId, BigDecimal amount) {
        int updated = playerRepository.addBalance(userId, amount);
        if (updated == 0) {
            throw new RuntimeException("Player not found for user: " + userId);
        }
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void freezeBalance(Long userId, BigDecimal amount) {
        int updated = playerRepository.freezeBalance(userId, amount);
        if (updated == 0) {
            throw new RuntimeException("Insufficient balance");
        }
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void unfreezeBalance(Long userId, BigDecimal amount) {
        int updated = playerRepository.unfreezeBalance(userId, amount);
        if (updated == 0) {
            throw new RuntimeException("Insufficient frozen balance");
        }
    }

    @Transactional(transactionManager = "tenantTransactionManager")
    public void returnFrozenBalance(Long userId, BigDecimal amount) {
        int updated = playerRepository.returnFrozenBalance(userId, amount);
        if (updated == 0) {
            throw new RuntimeException("Insufficient frozen balance");
        }
    }

    private Player findPlayerEntityByUserId(Long userId) {
        return playerRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Player not found for user: " + userId));
    }

    private Player findPlayerEntityOrNullByUserId(Long userId) {
        return playerRepository.findByUserId(userId).orElse(null);
    }
}
