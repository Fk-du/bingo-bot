package com.bingo.app.infrastructure.repository;

import com.bingo.app.infrastructure.entity.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    Optional<InviteCode> findByCode(String code);

    List<InviteCode> findByAdminId(Long adminId);

    boolean existsByCode(String code);
}
