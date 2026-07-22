package com.daesung.sales.dashboard.repository;

import com.daesung.sales.dashboard.entity.SalesTarget;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesTargetRepository extends JpaRepository<SalesTarget, Long> {

    /** upsert용 조회. productId null(전사)도 정확히 매칭. */
    @Query("select t from SalesTarget t where t.fiscalYear = :year and t.month = :month "
            + "and ((:productId is null and t.productId is null) or t.productId = :productId)")
    Optional<SalesTarget> find(@Param("year") int year, @Param("month") int month,
                              @Param("productId") Long productId);

    /** 연도의 목표(전사 또는 특정 상품). productId null이면 전사(product_id is null)만. */
    @Query("select t from SalesTarget t where t.fiscalYear = :year "
            + "and ((:productId is null and t.productId is null) or t.productId = :productId) "
            + "order by t.month")
    List<SalesTarget> findByYear(@Param("year") int year, @Param("productId") Long productId);
}
