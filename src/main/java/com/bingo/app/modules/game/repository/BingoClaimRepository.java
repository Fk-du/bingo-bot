package com.bingo.app.modules.game.repository;

import com.bingo.app.modules.game.entity.BingoClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BingoClaimRepository extends JpaRepository<BingoClaim, Long> {

    List<BingoClaim> findByGameId(Long gameId);

    List<BingoClaim> findByGameIdAndPlayerId(Long gameId, Long playerId);

    List<BingoClaim> findByGameIdAndResult(Long gameId, String result);
}
