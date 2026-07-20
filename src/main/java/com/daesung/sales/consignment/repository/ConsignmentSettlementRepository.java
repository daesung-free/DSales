package com.daesung.sales.consignment.repository;

import com.daesung.sales.consignment.entity.ConsignmentSettlement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsignmentSettlementRepository extends JpaRepository<ConsignmentSettlement, Long> {
    List<ConsignmentSettlement> findByConsignmentOutId(Long consignmentOutId);
}
