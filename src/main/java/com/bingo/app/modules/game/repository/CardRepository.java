package com.bingo.app.modules.game.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

import com.bingo.app.modules.game.entity.Card;

public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findFirstByUsedFalse();

    List<Card> findByUsedFalse();

    List<Card> findByUsedTrue();

    Page<Card> findByUsedFalse(Pageable pageable);
}
