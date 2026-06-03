package com.bingo.app.tenant.repository;

import com.bingo.app.tenant.entity.GameCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameCardRepository extends JpaRepository<GameCard, Long> {

    List<GameCard> findByPlayerIdOrderByCreatedAtDesc(Long playerId);

    Optional<GameCard> findByGameIdAndPlayerId(Long gameId, Long playerId);

    boolean existsByGameIdAndPlayerId(Long gameId, Long playerId);

    int countByGameId(Long gameId);

    List<GameCard> findByGameId(Long gameId);

    List<GameCard> findByGameIdAndWinnerTrue(Long gameId);

    @Query("SELECT COUNT(DISTINCT gc.playerId) FROM GameCard gc WHERE gc.gameId = :gameId")
    long countDistinctPlayersByGameId(@Param("gameId") Long gameId);

    @Query("SELECT gc FROM GameCard gc WHERE gc.gameId = :gameId AND gc.winner = true")
    Optional<GameCard> findWinnerByGameId(@Param("gameId") Long gameId);
}