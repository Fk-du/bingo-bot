package com.bingo.app.modules.game.repository;

import com.bingo.app.modules.game.enums.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import com.bingo.app.modules.game.entity.Game;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByAdminId(Long adminId);

    List<Game> findByAdminIdAndStatus(Long adminId, GameStatus status);

    Optional<Game> findFirstByAdminIdAndStatusOrderByCreatedAtAsc(Long adminId, GameStatus status);

    List<Game> findByStatus(GameStatus status);

    Optional<Game> findFirstByStatusOrderByCreatedAtAsc(GameStatus status);

    Optional<Game> findFirstByStatusOrderByStartTimeAsc(GameStatus status);
}
