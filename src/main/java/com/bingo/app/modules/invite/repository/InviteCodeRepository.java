package com.bingo.app.modules.invite.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

import com.bingo.app.modules.invite.entity.InviteCode;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    Optional<InviteCode> findByCode(String code);

    List<InviteCode> findByAdminId(Long adminId);

    boolean existsByCode(String code);
}
