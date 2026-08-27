package com.daesung.sales.consignment.repository;

import com.daesung.sales.consignment.entity.SettlementDraft;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SettlementDraftRepository extends JpaRepository<SettlementDraft, Long> {

    /**
     * 초안 목록(최근 저장순). 행·미결·상품·거래처를 함께 읽는다 —
     * 목록 화면이 행마다 도서명·거래처명을 보여주므로 지연로딩이면 N+1이 난다.
     */
    @Query("""
            select distinct d from SettlementDraft d
              left join fetch d.lines l
              left join fetch l.consignmentOut o
              left join fetch o.product
              left join fetch o.partner
            order by d.createdAt desc, d.id desc
            """)
    List<SettlementDraft> findAllWithLines();

    /** 초안번호로 조회(화면이 draftId로 부른다). */
    Optional<SettlementDraft> findByDraftNo(String draftNo);
}
