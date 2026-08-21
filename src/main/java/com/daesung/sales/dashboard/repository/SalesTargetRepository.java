package com.daesung.sales.dashboard.repository;

import com.daesung.sales.dashboard.entity.SalesTarget;
import com.daesung.sales.dashboard.entity.TargetEntryType;
import com.daesung.sales.dashboard.entity.TargetScope;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesTargetRepository extends JpaRepository<SalesTarget, Long> {

    /**
     * upsert용 단건 조회. month·scopeKey·productId가 null인 조합(연간·전사)도 정확히 매칭해야 하므로
     * 널 비교를 명시적으로 쓴다 — {@code = null}은 항상 false라 조용히 중복 행이 생긴다.
     */
    @Query("""
            select t from SalesTarget t
             where t.fiscalYear = :year
               and ((:month is null and t.month is null) or t.month = :month)
               and t.scope = :scope
               and ((:scopeKey is null and t.scopeKey is null) or t.scopeKey = :scopeKey)
               and ((:productId is null and t.productId is null) or t.productId = :productId)
               and t.entryType = :entryType
            """)
    Optional<SalesTarget> find(@Param("year") int year, @Param("month") Integer month,
                               @Param("scope") TargetScope scope, @Param("scopeKey") String scopeKey,
                               @Param("productId") Long productId,
                               @Param("entryType") TargetEntryType entryType);

    /**
     * 해당 연도·축의 <b>전 행</b>(상품이 무엇이든). 19p 제품별 목표대비처럼
     * "상품 목표 전부"가 필요할 때 쓴다.
     *
     * <p>‼️{@link #findByYearAndScope}에 productId=null을 넘기면 <b>productId가 NULL인 행</b>만
     * 나온다(그 조건이 "전체"가 아니라 "널인 것"이다). 상품 목표는 productId가 채워져 있어 하나도 안 걸린다.
     */
    List<SalesTarget> findByFiscalYearAndScope(int fiscalYear, TargetScope scope);

    /** 해당 연도·대상의 전 행(월별 + 연간). 대시보드가 월 셀과 연간 요약을 함께 조립한다. */
    @Query("""
            select t from SalesTarget t
             where t.fiscalYear = :year
               and t.scope = :scope
               and ((:scopeKey is null and t.scopeKey is null) or t.scopeKey = :scopeKey)
               and ((:productId is null and t.productId is null) or t.productId = :productId)
             order by t.month
            """)
    List<SalesTarget> findByYearAndScope(@Param("year") int year, @Param("scope") TargetScope scope,
                                         @Param("scopeKey") String scopeKey,
                                         @Param("productId") Long productId);

    /** 목표 목록 조회(관리 화면). 연도 필터만. */
    List<SalesTarget> findByFiscalYearOrderByScopeAscScopeKeyAscMonthAsc(int fiscalYear);
}
