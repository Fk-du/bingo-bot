package com.bingo.app.modules.game.service;

import com.bingo.app.infrastructure.entity.TenantRegistry;
import com.bingo.app.infrastructure.repository.TenantRegistryRepository;
import com.bingo.app.infrastructure.tenant.TenantContext;
import com.bingo.app.modules.game.entity.Game;
import com.bingo.app.modules.game.enums.GameStatus;
import com.bingo.app.modules.game.repository.GameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTaskService {

    private final TenantRegistryRepository tenantRegistryRepository;
    private final GameRepository gameRepository;

    @Value("${bingo.schedules.stale-registration-hours:24}")
    private int staleRegistrationHours;

    @Value("${bingo.schedules.stale-claim-minutes:30}")
    private int staleClaimMinutes;

    @Value("${bingo.schedules.stale-in-progress-hours:6}")
    private int staleInProgressHours;

    @Scheduled(fixedDelayString = "${bingo.schedules.cleanup-interval-ms:600000}")
    public void cleanupStaleGames() {
        TenantContext.set(TenantContext.masterTenant());
        try {
            List<TenantRegistry> tenants = tenantRegistryRepository.findAll();
            for (TenantRegistry tenant : tenants) {
                String tenantId = TenantContext.agentTenant(tenant.getAdminId());
                TenantContext.set(tenantId);
                try {
                    int cleaned = cleanupStaleGamesForCurrentTenant();
                    if (cleaned > 0) {
                        log.info("Cleaned {} stale game(s) for tenant {}", cleaned, tenantId);
                    }
                } catch (Exception e) {
                    log.error("Error cleaning stale games for tenant {}: {}", tenantId, e.getMessage());
                } finally {
                    TenantContext.clear();
                }
            }
        } finally {
            TenantContext.clear();
        }
    }

    private int cleanupStaleGamesForCurrentTenant() {
        int cleaned = 0;
        LocalDateTime now = LocalDateTime.now();

        List<Game> registrationGames = gameRepository.findByStatus(GameStatus.REGISTRATION_OPEN);
        for (Game game : registrationGames) {
            if (Duration.between(game.getCreatedAt(), now).toHours() >= staleRegistrationHours) {
                game.setStatus(GameStatus.ENDED);
                gameRepository.save(game);
                cleaned++;
                log.debug("Ended stale REGISTRATION_OPEN game {} (created at {})", game.getId(), game.getCreatedAt());
            }
        }

        List<Game> claimPendingGames = gameRepository.findByStatus(GameStatus.CLAIM_PENDING);
        for (Game game : claimPendingGames) {
            if (Duration.between(game.getCreatedAt(), now).toMinutes() >= staleClaimMinutes) {
                game.setStatus(GameStatus.ENDED);
                gameRepository.save(game);
                cleaned++;
                log.debug("Ended stale CLAIM_PENDING game {} (created at {})", game.getId(), game.getCreatedAt());
            }
        }

        List<Game> inProgressGames = gameRepository.findByStatus(GameStatus.IN_PROGRESS);
        for (Game game : inProgressGames) {
            if (game.getStartTime() != null
                    && Duration.between(game.getStartTime(), now).toHours() >= staleInProgressHours) {
                game.setStatus(GameStatus.ENDED);
                gameRepository.save(game);
                cleaned++;
                log.debug("Ended stale IN_PROGRESS game {} (started at {})", game.getId(), game.getStartTime());
            }
        }

        return cleaned;
    }
}
