package com.daesung.sales.dashboard.repository;

import com.daesung.sales.dashboard.entity.DashboardSnapshot;
import com.daesung.sales.dashboard.entity.TargetScope;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DashboardSnapshotRepository extends JpaRepository<DashboardSnapshot, Long> {

    /** 연도·축의 월별 스냅샷. productId가 null이면 전사(product_id is null)만. */
    @Query("""
            select s from DashboardSnapshot s
             where s.fiscalYear = :year and s.scope = :scope
               and ((:productId is null and s.productId is null) or s.productId = :productId)
            """)
    List<DashboardSnapshot> findByYearAndScope(@Param("year") int year,
                                               @Param("scope") TargetScope scope,
                                               @Param("productId") Long productId);

    @Query("""
            select s from DashboardSnapshot s
             where s.fiscalYear = :year and s.month = :month and s.scope = :scope
               and ((:productId is null and s.productId is null) or s.productId = :productId)
            """)
    Optional<DashboardSnapshot> findOne(@Param("year") int year, @Param("month") int month,
                                        @Param("scope") TargetScope scope,
                                        @Param("productId") Long productId);
}
