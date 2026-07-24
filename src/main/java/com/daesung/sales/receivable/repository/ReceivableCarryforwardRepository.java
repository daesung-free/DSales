package com.daesung.sales.receivable.repository;

import com.daesung.sales.receivable.entity.ReceivableCarryforward;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceivableCarryforwardRepository extends JpaRepository<ReceivableCarryforward, Long> {

    /** 특정 연도의 이월 스냅샷 전량 삭제(idempotent 재생성용). */
    @Modifying
    @Query("delete from ReceivableCarryforward r where r.fiscalYear = :year")
    int deleteByFiscalYear(@Param("year") int year);

    /** 연도별 거래처 이월 합계. 반환 Object[]: [partnerId, carryAmount]. */
    @Query(value = """
            SELECT r.partner_id, COALESCE(SUM(r.carry_amount),0)
            FROM receivable_carryforward r
            WHERE r.fiscal_year = :year
              AND (CAST(:partnerId AS SIGNED) IS NULL OR r.partner_id = :partnerId)
            GROUP BY r.partner_id
            """, nativeQuery = true)
    List<Object[]> sumByYear(@Param("year") int year, @Param("partnerId") Long partnerId);
}
