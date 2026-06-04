package com.bingo.app.tenant.repository;

import com.bingo.app.tenant.entity.BingoClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BingoClaimRepository extends JpaRepository<BingoClaim, Long> {

    List<BingoClaim> findByGameIdAndResult(Long gameId, String result);

    List<BingoClaim> findByGameId(Long gameId);

    List<BingoClaim> findByPlayerId(Long playerId);

    boolean existsByGameIdAndPlayerIdAndResult(Long gameId, Long playerId, String result);

    Optional<BingoClaim> findByGameIdAndPlayerIdAndResult(Long gameId, Long playerId, String result);

    @Query("SELECT c FROM BingoClaim c WHERE c.gameId = :gameId AND c.result = :result ORDER BY c.claimedAt ASC")
    List<BingoClaim> findByGameIdAndResultOrderByClaimedAt(@Param("gameId") Long gameId, @Param("result") String result);

    @Query("SELECT c FROM BingoClaim c WHERE c.gameId = :gameId AND c.result = :result AND c.validatedAt IS NULL ORDER BY c.claimedAt ASC")
    List<BingoClaim> findByGameIdAndResultAndValidatedAtIsNull(@Param("gameId") Long gameId, @Param("result") String result);

    @Query("SELECT COUNT(c) FROM BingoClaim c WHERE c.gameId = :gameId AND c.result = :result AND c.validatedAt IS NULL")
    long countByGameIdAndResultAndValidatedAtIsNull(@Param("gameId") Long gameId, @Param("result") String result);

    @Query("SELECT COUNT(c) FROM BingoClaim c WHERE c.gameId = :gameId AND c.result = :result AND c.validatedAt IS NOT NULL")
    long countByGameIdAndResultAndValidatedAtIsNotNull(@Param("gameId") Long gameId, @Param("result") String result);

    @Query("SELECT c FROM BingoClaim c WHERE c.gameId = :gameId ORDER BY c.claimedAt DESC")
    List<BingoClaim> findByGameIdOrderByClaimedAtDesc(@Param("gameId") Long gameId);
}