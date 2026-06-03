package com.bingo.app.tenant.repository;

import com.bingo.app.tenant.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    Optional<Card> findByNumbersHash(String numbersHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Card c WHERE c.id = :id")
    Optional<Card> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT c FROM Card c WHERE c.used = false ORDER BY c.usageCount ASC")
    List<Card> findAvailableCards();

    long countByUsedFalse();

    @Query("SELECT c FROM Card c WHERE c.used = true ORDER BY c.usageCount DESC")
    List<Card> findMostUsedCards();

    @Query("SELECT AVG(c.winRate) FROM Card c")
    Double getAverageWinRate();
}