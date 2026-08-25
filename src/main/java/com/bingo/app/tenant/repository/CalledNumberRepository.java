package com.bingo.app.tenant.repository;

import com.bingo.app.tenant.entity.CalledNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CalledNumberRepository extends JpaRepository<CalledNumber, Long> {

    // Find by game and sequence index
    Optional<CalledNumber> findByGameIdAndSequenceIndex(Long gameId, Integer sequenceIndex);

    // Find by game and number
    Optional<CalledNumber> findByGameIdAndNumber(Long gameId, Integer number);

    // Find by game, number, and check if called at is not null
    Optional<CalledNumber> findByGameIdAndNumberAndCalledAtIsNotNull(Long gameId, Integer number);

    // Find all called numbers for a game (only those that have been called)
    @Query("SELECT c.number FROM CalledNumber c WHERE c.gameId = :gameId AND c.calledAt IS NOT NULL ORDER BY c.sequenceIndex")
    List<Integer> findCalledNumbersByGameId(@Param("gameId") Long gameId);

    // Find all numbers in sequence for a game
    @Query("SELECT c FROM CalledNumber c WHERE c.gameId = :gameId ORDER BY c.sequenceIndex")
    List<CalledNumber> findAllByGameIdOrderBySequenceIndex(@Param("gameId") Long gameId);

    // Check if number has been called
    boolean existsByGameIdAndNumberAndCalledAtIsNotNull(Long gameId, Integer number);

    // Count called numbers for a game
    long countByGameIdAndCalledAtIsNotNull(Long gameId);

    // Get next number to call
    @Query("SELECT c FROM CalledNumber c WHERE c.gameId = :gameId AND c.calledAt IS NULL ORDER BY c.sequenceIndex LIMIT 1")
    Optional<CalledNumber> findNextNumberToCall(@Param("gameId") Long gameId);

    // Get all called numbers with their call times
    @Query("SELECT c FROM CalledNumber c WHERE c.gameId = :gameId AND c.calledAt IS NOT NULL ORDER BY c.sequenceIndex")
    List<CalledNumber> findCalledNumbersWithDetails(@Param("gameId") Long gameId);

    // Get the last called number
    @Query("SELECT c FROM CalledNumber c WHERE c.gameId = :gameId AND c.calledAt IS NOT NULL ORDER BY c.sequenceIndex DESC LIMIT 1")
    Optional<CalledNumber> findLastCalledNumber(@Param("gameId") Long gameId);

    // Get called numbers count
    @Query("SELECT COUNT(c) FROM CalledNumber c WHERE c.gameId = :gameId AND c.calledAt IS NOT NULL")
    long getCalledNumbersCount(@Param("gameId") Long gameId);

    @Modifying
    @Query("DELETE FROM CalledNumber c WHERE c.gameId = :gameId")
    void deleteByGameId(@Param("gameId") Long gameId);
}