package com.bingo.app.modules.user.repository;

import com.bingo.app.modules.user.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

import com.bingo.app.modules.user.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramId(Long telegramId);

    List<User> findByParentId(Long parentId);

    List<User> findByRole(Role role);

    List<User> findByParentIdAndRole(Long parentId, Role role);
}
