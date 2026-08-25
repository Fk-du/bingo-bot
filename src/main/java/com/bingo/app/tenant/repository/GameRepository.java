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

    Optional<Game> findByAdminUserIdAndStatus(Long adminUserId, GameStatus status);

    @Query("SELECT g FROM Game g WHERE g.adminUserId = :adminUserId AND g.status IN :statuses")
    List<Game> findAllByAdminUserIdAndStatusIn(@Param("adminUserId") Long adminUserId, @Param("statuses") List<GameStatus> statuses);

    List<Game> findAllByAdminUserIdOrderByCreatedAtDesc(Long adminUserId);

    List<Game> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COUNT(g) FROM Game g WHERE g.adminUserId = :adminUserId AND g.status IN :statuses")
    long countByAdminUserIdAndStatusIn(@Param("adminUserId") Long adminUserId, @Param("statuses") List<GameStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Game g WHERE g.id = :id")
    Optional<Game> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT g FROM Game g WHERE g.adminUserId = :adminUserId AND g.status NOT IN ('ENDED') ORDER BY g.createdAt DESC")
    Optional<Game> findActiveGameByAdmin(@Param("adminUserId") Long adminUserId);

    @Query("SELECT COUNT(g) > 0 FROM Game g WHERE g.adminUserId = :adminUserId AND g.status IN ('STARTING', 'IN_PROGRESS', 'PAUSED', 'CLAIM_PENDING')")
    boolean hasActiveGame(@Param("adminUserId") Long adminUserId);

    List<Game> findByStatus(GameStatus status);
}
