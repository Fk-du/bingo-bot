package com.bingo.app.master.repository;

import com.bingo.app.master.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId);
}