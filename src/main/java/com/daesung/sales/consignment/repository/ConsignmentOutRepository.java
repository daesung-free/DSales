package com.daesung.sales.consignment.repository;

import com.daesung.sales.consignment.entity.ConsignmentOut;
import com.daesung.sales.consignment.entity.ConsignmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConsignmentOutRepository extends JpaRepository<ConsignmentOut, Long> {
    Optional<ConsignmentOut> findBySourceOutNo(String sourceOutNo);

    List<ConsignmentOut> findByPartnerIdAndStatus(Long partnerId, ConsignmentStatus status);

    /** 특정 거래처의 미결(잔여>0) 위탁출고. product를 함께 로드해 N+1 방지. */
    @Query("select c from ConsignmentOut c join fetch c.product"
            + " where c.partner.id = :partnerId and c.remainingQty > 0 order by c.id")
    List<ConsignmentOut> findPending(Long partnerId);
}
