package com.daesung.sales.receivable.repository;

import com.daesung.sales.receivable.entity.Collection;
import com.daesung.sales.receivable.entity.CollectionType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionRepository extends JpaRepository<Collection, Long> {

    /** 수금 목록(기간·거래처 + 수금구분·입금구분 두 축 필터). 근거: 정본 23p 데이터 항목. */
    @Query("select c from Collection c "
            + "where (:from is null or c.collDate >= :from) "
            + "and (:to is null or c.collDate <= :to) "
            + "and (:partnerId is null or c.partner.id = :partnerId) "
            + "and (:collKind is null or c.collKind = :collKind) "
            + "and (:collType is null or c.collType = :collType)")
    Page<Collection> search(@Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            @Param("partnerId") Long partnerId,
                            @Param("collKind") String collKind,
                            @Param("collType") CollectionType collType,
                            Pageable pageable);

    /** 거래처별 수금 합계(기간). 반환 Object[]: [partnerId, collSum]. */
    @Query(value = """
            SELECT c.partner_id, COALESCE(SUM(c.coll_amt),0)
            FROM collection c
            WHERE c.coll_date BETWEEN :fromDate AND :toDate
              AND (CAST(:partnerId AS SIGNED) IS NULL OR c.partner_id = :partnerId)
            GROUP BY c.partner_id
            """, nativeQuery = true)
    List<Object[]> sumByPartner(@Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate,
                                @Param("partnerId") Long partnerId);

    /** 외상매출장 명세: 특정 거래처의 기간 내 수금 라인(일자순). */
    @Query("select c from Collection c where c.partner.id = :partnerId "
            + "and c.collDate between :from and :to order by c.collDate, c.id")
    List<Collection> findLedgerLines(@Param("partnerId") Long partnerId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);
}
