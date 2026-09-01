package com.daesung.sales.consignment.repository;

import com.daesung.sales.consignment.entity.ConsignmentOut;
import com.daesung.sales.consignment.entity.ConsignmentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ConsignmentOutRepository extends JpaRepository<ConsignmentOut, Long> {
    Optional<ConsignmentOut> findBySourceOutNo(String sourceOutNo);

    /**
     * 미결 1건을 비관적 쓰기락(SELECT ... FOR UPDATE)으로 로드. 정산/반품의 read-modify-write를
     * 동일 행에 대해 직렬화 → 동시 정산 시 lost update·초과정산 방지(재고의 원자적 UPDATE와 대칭).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConsignmentOut c where c.id = :id")
    Optional<ConsignmentOut> findByIdForUpdate(Long id);

    List<ConsignmentOut> findByPartnerIdAndStatus(Long partnerId, ConsignmentStatus status);

    /** 특정 거래처의 미결(잔여>0) 위탁출고. product를 함께 로드해 N+1 방지. */
    @Query("select c from ConsignmentOut c join fetch c.product"
            + " where c.partner.id = :partnerId and c.remainingQty <> 0 order by c.id")
    List<ConsignmentOut> findPending(Long partnerId);
    // ★조건이 `> 0`이 아니라 `<> 0`인 이유: 초과정산 차단을 제거한 뒤(발주처 2026-08-31)
    //   잔여가 **음수**가 될 수 있다. `> 0`으로 두면 초과된 미결이 화면에서 사라져
    //   "경고만 주고 담당자가 수기로 정리한다"는 요구를 만족할 수 없다 — 보여야 고친다.

    /**
     * 전 거래처 미결 백로그(건수·잔여수량 합). 대시보드 '위탁 정산 대기' 카드용.
     *
     * <p>거래처별 조회({@link #findPending})를 전 거래처로 돌려 세면 되지만,
     * 그러면 거래처 수만큼 쿼리가 나가고 화면은 <b>숫자 두 개</b>만 쓴다.
     * <p>‼️반환 타입을 {@code Object[]}로 쓰면 안 된다 — Hibernate가 <b>행 배열을 한 겹 더 감싸</b>
     * {@code Object[]{Object[]{건수, 합계}}}로 준다. 그대로 읽으면 ClassCastException이 나고
     * 화면에는 카드가 통째로 사라진다(조용히 틀리는 쪽이라 실제로 한 번 겪었다).
     * 스칼라 두 개는 따로 세는 편이 안전하고, 쿼리도 그대로 인덱스를 탄다.
     */
    @Query("select count(c) from ConsignmentOut c where c.remainingQty <> 0")
    long countPending();

    /** 미결 잔여수량 합. {@link #countPending}과 짝. */
    @Query("select coalesce(sum(c.remainingQty), 0) from ConsignmentOut c where c.remainingQty > 0")
    long sumPendingQty();
}
