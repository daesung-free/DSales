package com.daesung.sales.inventory.repository;

import com.daesung.sales.inventory.entity.VoucherCancel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherCancelRepository extends JpaRepository<VoucherCancel, Long> {

    /** 같은 전표를 두 번 취소하면 재고가 반대로 밀린다. 호출 전에 반드시 확인한다. */
    boolean existsByRefNo(String refNo);

    /**
     * 취소된 전표번호들을 한 번에(목록 화면의 '취소' 표시용).
     * 행마다 조회하면 N+1 이라 페이지에 실린 번호만 모아 한 번 묻는다.
     */
    @org.springframework.data.jpa.repository.Query(
            "select v.refNo from VoucherCancel v where v.refNo in :refNos")
    java.util.List<String> findRefNosIn(
            @org.springframework.data.repository.query.Param("refNos") java.util.Collection<String> refNos);
}
