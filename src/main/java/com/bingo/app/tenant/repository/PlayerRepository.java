package com.bingo.app.tenant.repository;

import com.bingo.app.tenant.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findByUserId(Long userId);

    List<Player> findByAdminUserId(Long adminUserId);

    List<Player> findByParentId(Long parentId);

    long countByAdminUserId(Long adminUserId);

    long countByParentId(Long parentId);

    @Modifying
    @Query("UPDATE Player p SET p.balance = p.balance - :amount WHERE p.userId = :userId AND p.balance >= :amount")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE Player p SET p.balance = p.balance + :amount WHERE p.userId = :userId")
    int addBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE Player p SET p.balance = p.balance - :amount, p.frozenBalance = p.frozenBalance + :amount WHERE p.userId = :userId AND p.balance >= :amount")
    int freezeBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE Player p SET p.frozenBalance = p.frozenBalance - :amount WHERE p.userId = :userId AND p.frozenBalance >= :amount")
    int unfreezeBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE Player p SET p.balance = p.balance + :amount, p.frozenBalance = p.frozenBalance - :amount WHERE p.userId = :userId AND p.frozenBalance >= :amount")
    int returnFrozenBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
