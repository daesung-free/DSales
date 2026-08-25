package com.daesung.sales.permission.repository;

import com.daesung.sales.auth.entity.Role;
import com.daesung.sales.permission.entity.RoleScreenPermission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleScreenPermissionRepository extends JpaRepository<RoleScreenPermission, Long> {

    @Query("select p from RoleScreenPermission p join fetch p.screen order by p.role, p.screen.sortOrder")
    List<RoleScreenPermission> findAllWithScreen();

    Optional<RoleScreenPermission> findByRoleAndScreenId(Role role, Long screenId);
}
