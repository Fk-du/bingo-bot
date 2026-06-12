package com.bingo.app.master.repository;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramId(Long telegramId);

    List<User> findAllByRole(Role role);

    List<User> findAllByAdminUserId(Long adminUserId);

    List<User> findAllByParentIdAndRole(Long parentId, Role role);

    boolean existsByTelegramId(Long telegramId);

    Optional<User> findById(Long id);

    @Modifying
    @Query("UPDATE User u SET u.balance = u.balance - :amount WHERE u.id = :userId AND u.balance >= :amount")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    long countByParentId(Long parentId);

    long countByRole(Role role);

    @Modifying
    @Query("UPDATE User u SET u.balance = u.balance + :amount WHERE u.id = :userId")
    void addBalance(Long userId, BigDecimal amount);
}
