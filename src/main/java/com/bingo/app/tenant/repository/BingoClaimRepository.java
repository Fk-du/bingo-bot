package com.bingo.app.tenant.repository;

import com.bingo.app.tenant.entity.BingoClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BingoClaimRepository extends JpaRepository<BingoClaim, Long> {

    List<BingoClaim> findByGameIdAndResult(Long gameId, String result);

    List<BingoClaim> findByGameId(Long gameId);

    List<BingoClaim> findByPlayerId(Long playerId);

    boolean existsByGameIdAndPlayerIdAndResult(Long gameId, Long playerId, String result);
}