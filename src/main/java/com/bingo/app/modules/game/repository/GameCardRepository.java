package com.bingo.app.modules.game.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import com.bingo.app.modules.game.entity.GameCard;

public interface GameCardRepository extends JpaRepository<GameCard, Long> {

    List<GameCard> findByGameId(Long gameId);

    List<GameCard> findByPlayerId(Long playerId);

    List<GameCard> findByGameIdAndPlayerId(Long gameId, Long playerId);
}
