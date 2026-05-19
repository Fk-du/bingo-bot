package com.bingo.app.modules.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import com.bingo.app.modules.game.entity.CalledNumber;

public interface CalledNumberRepository extends JpaRepository<CalledNumber, Long> {

    List<CalledNumber> findByGameId(Long gameId);

    List<CalledNumber> findByGameIdOrderBySequenceIndexAsc(Long gameId);

    Optional<CalledNumber> findByGameIdAndSequenceIndex(Long gameId, Integer sequenceIndex);

    List<CalledNumber> findByGameIdAndCalledAtIsNotNullOrderBySequenceIndexAsc(Long gameId);
}
