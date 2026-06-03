package com.bingo.app.master.repository;

import com.bingo.app.master.entity.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {
    Optional<InviteCode> findByCodeAndActiveTrue(String code);

    List<InviteCode> findByCreatorIdAndActiveTrue(Long creatorId);

    List<InviteCode> findByCreatorId(Long creatorId);

    boolean existsByCode(String code);

    @Modifying
    @Query("UPDATE InviteCode ic SET ic.active = false WHERE ic.code = :code")
    void deactivateInviteCode(@Param("code") String code);
}

