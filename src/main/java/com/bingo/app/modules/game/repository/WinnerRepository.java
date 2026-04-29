package com.bingo.app.modules.game.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.bingo.app.modules.game.entity.Winner;

import java.util.List;

public interface WinnerRepository extends JpaRepository<Winner, Long> {

    List<Winner> findByGameId(Long gameId);

    List<Winner> findByPlayerId(Long playerId);
}
