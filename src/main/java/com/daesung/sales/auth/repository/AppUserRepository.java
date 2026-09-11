package com.daesung.sales.auth.repository;

import com.daesung.sales.auth.entity.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 활성 상태인 특정 역할의 수 — 마지막 관리자를 끄지 못하게 막는 데 쓴다. */
    long countByRoleAndActiveTrue(com.daesung.sales.auth.entity.Role role);
}
