package com.slotlock.application.repository;

import com.slotlock.application.entity.User;
import com.slotlock.application.enums.UserRoleEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(UserRoleEnum role);

    boolean existsByTenantId(Long tenantId);
}
