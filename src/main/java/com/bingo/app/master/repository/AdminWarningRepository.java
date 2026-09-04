package com.bingo.app.master.repository;

import com.bingo.app.master.entity.AdminWarning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminWarningRepository extends JpaRepository<AdminWarning, Long> {

    List<AdminWarning> findByAdminUserIdOrderByCreatedAtDesc(Long adminUserId);
}
