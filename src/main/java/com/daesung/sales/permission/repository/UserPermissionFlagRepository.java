package com.daesung.sales.permission.repository;

import com.daesung.sales.permission.entity.UserPermissionFlag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPermissionFlagRepository extends JpaRepository<UserPermissionFlag, Long> {

    List<UserPermissionFlag> findByUserId(Long userId);

    Optional<UserPermissionFlag> findByUserIdAndFlagKey(Long userId, String flagKey);
}
