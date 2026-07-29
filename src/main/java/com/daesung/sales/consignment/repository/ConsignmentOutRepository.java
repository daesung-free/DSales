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
            + " where c.partner.id = :partnerId and c.remainingQty > 0 order by c.id")
    List<ConsignmentOut> findPending(Long partnerId);
}
