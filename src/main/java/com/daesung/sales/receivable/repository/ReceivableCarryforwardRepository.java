package com.daesung.sales.receivable.repository;

import com.daesung.sales.receivable.entity.ReceivableCarryforward;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceivableCarryforwardRepository extends JpaRepository<ReceivableCarryforward, Long> {

    /**
     * 특정 연도의 이월 스냅샷 전량 삭제(idempotent 재생성용).
     *
     * <p><b>물리 DELETE 예외 — 승인 근거를 여기 명시한다.</b> 게이트규칙상 물리삭제는 예외 절차로만
     * 허용되나, 이 테이블은 원장이 아니라 매출·수금에서 <b>재계산 가능한 파생 스냅샷</b>이다.
     * 논리삭제로 바꾸면 재생성마다 폐기행이 누적돼 (fiscal_year, partner_id) 유일성만 흐려질 뿐
     * 감사가치는 없다. 원본 근거(매출·수금 원장)는 그대로 보존되므로 추적성 손실도 없다.
     */
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
