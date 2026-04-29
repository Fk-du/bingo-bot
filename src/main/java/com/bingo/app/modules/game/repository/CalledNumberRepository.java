package com.bingo.app.modules.game.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import com.bingo.app.modules.game.entity.CalledNumber;

public interface CalledNumberRepository extends JpaRepository<CalledNumber, Long> {

    List<CalledNumber> findByGameId(Long gameId);

    List<CalledNumber> findByGameIdOrderByCalledAtAsc(Long gameId);
}
