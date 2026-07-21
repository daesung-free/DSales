package com.daesung.sales.receivable.repository;

import com.daesung.sales.receivable.entity.Collection;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionRepository extends JpaRepository<Collection, Long> {

    /** 수금번호(C) 채번용 시퀀스. 레거시 (수금일+거래처) Max+1 대체. */
    @Query(value = "SELECT nextval('seq_collection_no')", nativeQuery = true)
    long nextCollectionSeq();

    /** 수금 목록(기간·거래처 필터). */
    @Query("select c from Collection c "
            + "where (:from is null or c.collDate >= :from) "
            + "and (:to is null or c.collDate <= :to) "
            + "and (:partnerId is null or c.partner.id = :partnerId)")
    Page<Collection> search(@Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            @Param("partnerId") Long partnerId,
                            Pageable pageable);

    /** 거래처별 수금 합계(기간). 반환 Object[]: [partnerId, collSum]. */
    @Query(value = """
            SELECT c.partner_id, COALESCE(SUM(c.coll_amt),0)
            FROM collection c
            WHERE c.coll_date BETWEEN :fromDate AND :toDate
              AND (CAST(:partnerId AS bigint) IS NULL OR c.partner_id = :partnerId)
            GROUP BY c.partner_id
            """, nativeQuery = true)
    List<Object[]> sumByPartner(@Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate,
                                @Param("partnerId") Long partnerId);
}
