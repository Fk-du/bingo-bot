package com.bingo.app.master.repository;

import com.bingo.app.master.entity.AdminFundRequest;
import com.bingo.app.master.enums.FundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminFundRequestRepository extends JpaRepository<AdminFundRequest, Long> {

    List<AdminFundRequest> findByAdminUserIdOrderByCreatedAtDesc(Long adminUserId);

    List<AdminFundRequest> findByStatusOrderByCreatedAtDesc(FundStatus status);

    List<AdminFundRequest> findAllByOrderByCreatedAtDesc();
}
