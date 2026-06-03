package com.bingo.app.tenant.repository;

import com.bingo.app.tenant.entity.PlayerCard;
import com.bingo.app.tenant.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlayerCardRepository extends JpaRepository<PlayerCard, Long> {

    Optional<PlayerCard> findByPlayerIdAndStatus(Long playerId, AssignmentStatus status);

    @Modifying
    @Query("UPDATE PlayerCard pc SET pc.status = 'LOCKED' WHERE pc.playerId = :playerId AND pc.status = 'ACTIVE'")
    int lockPlayerCard(@Param("playerId") Long playerId);

    @Modifying
    @Query("UPDATE PlayerCard pc SET pc.status = 'ACTIVE' WHERE pc.playerId = :playerId AND pc.status = 'LOCKED'")
    int unlockPlayerCard(@Param("playerId") Long playerId);
}