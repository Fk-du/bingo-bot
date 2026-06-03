package com.bingo.app.tenant.repository;

import com.bingo.app.tenant.entity.Game;
import com.bingo.app.tenant.enums.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    // Find by agent and status
    Optional<Game> findByAgentIdAndStatus(Long agentId, GameStatus status);

    // Find by agent and multiple statuses
    @Query("SELECT g FROM Game g WHERE g.agentId = :agentId AND g.status IN :statuses")
    Optional<Game> findByAgentIdAndStatusIn(@Param("agentId") Long agentId, @Param("statuses") List<GameStatus> statuses);

    // Find all games for agent ordered by creation date
    List<Game> findAllByAgentIdOrderByCreatedAtDesc(Long agentId);

    // Find all games ordered by creation date
    List<Game> findAllByOrderByCreatedAtDesc();

    // Count active games for agent
    @Query("SELECT COUNT(g) FROM Game g WHERE g.agentId = :agentId AND g.status IN :statuses")
    long countByAgentIdAndStatusIn(@Param("agentId") Long agentId, @Param("statuses") List<GameStatus> statuses);

    // Find by ID with pessimistic lock for update
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Game g WHERE g.id = :id")
    Optional<Game> findByIdForUpdate(@Param("id") Long id);

    // Find active game for agent (any status except ENDED)
    @Query("SELECT g FROM Game g WHERE g.agentId = :agentId AND g.status != 'ENDED' ORDER BY g.createdAt DESC")
    Optional<Game> findActiveGameByAgent(@Param("agentId") Long agentId);

    // Check if agent has any active game
    @Query("SELECT COUNT(g) > 0 FROM Game g WHERE g.agentId = :agentId AND g.status IN ('REGISTRATION_OPEN', 'IN_PROGRESS', 'CLAIM_PENDING')")
    boolean hasActiveGame(@Param("agentId") Long agentId);
}