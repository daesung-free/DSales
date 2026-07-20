package com.daesung.sales.consignment.repository;

import com.daesung.sales.consignment.entity.ConsignmentOut;
import com.daesung.sales.consignment.entity.ConsignmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsignmentOutRepository extends JpaRepository<ConsignmentOut, Long> {
    Optional<ConsignmentOut> findBySourceOutNo(String sourceOutNo);
    List<ConsignmentOut> findByPartnerIdAndStatus(Long partnerId, ConsignmentStatus status);
}
