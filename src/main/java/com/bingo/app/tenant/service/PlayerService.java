package com.bingo.app.tenant.service;

import com.bingo.app.tenant.entity.Player;
import com.bingo.app.tenant.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayerService {

    private final PlayerRepository playerRepository;

    @Transactional
    public Player createPlayer(Long userId, Long agentId, Long parentId) {
        Player player = Player.builder()
                .userId(userId)
                .agentId(agentId)
                .parentId(parentId)
                .balance(BigDecimal.ZERO)
                .frozenBalance(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .build();
        return playerRepository.save(player);
    }

    @Transactional(readOnly = true)
    public Player findByUserId(Long userId) {
        return playerRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Player not found for user: " + userId));
    }

    @Transactional(readOnly = true)
    public Player findByUserIdOrNull(Long userId) {
        return playerRepository.findByUserId(userId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<Player> getPlayersByAgent(Long agentId) {
        return playerRepository.findByAgentId(agentId);
    }

    @Transactional(readOnly = true)
    public List<Player> getPlayersByParent(Long parentId) {
        return playerRepository.findByParentId(parentId);
    }

    @Transactional(readOnly = true)
    public long countByAgentId(Long agentId) {
        return playerRepository.countByAgentId(agentId);
    }

    @Transactional(readOnly = true)
    public long countByParentId(Long parentId) {
        return playerRepository.countByParentId(parentId);
    }

    @Transactional(readOnly = true)
    public List<Long> getPlayerIdsByAgent(Long agentId) {
        return playerRepository.findByAgentId(agentId).stream()
                .map(Player::getUserId)
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long userId) {
        return findByUserId(userId).getBalance();
    }

    @Transactional
    public void deductBalance(Long userId, BigDecimal amount) {
        int updated = playerRepository.deductBalance(userId, amount);
        if (updated == 0) {
            throw new RuntimeException("Insufficient balance");
        }
    }

    @Transactional
    public void addBalance(Long userId, BigDecimal amount) {
        playerRepository.addBalance(userId, amount);
    }

    @Transactional
    public void freezeBalance(Long userId, BigDecimal amount) {
        int updated = playerRepository.freezeBalance(userId, amount);
        if (updated == 0) {
            throw new RuntimeException("Insufficient balance");
        }
    }

    @Transactional
    public void unfreezeBalance(Long userId, BigDecimal amount) {
        int updated = playerRepository.unfreezeBalance(userId, amount);
        if (updated == 0) {
            throw new RuntimeException("Insufficient frozen balance");
        }
    }

    @Transactional
    public void returnFrozenBalance(Long userId, BigDecimal amount) {
        int updated = playerRepository.returnFrozenBalance(userId, amount);
        if (updated == 0) {
            throw new RuntimeException("Insufficient frozen balance");
        }
    }
}
