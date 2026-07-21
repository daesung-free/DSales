package com.daesung.sales.inventory.repository;

import com.daesung.sales.inventory.entity.InventoryTxn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryTxnRepository extends JpaRepository<InventoryTxn, Long> {
    List<InventoryTxn> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    /** 폐기번호(P) 채번용 시퀀스. */
    @Query(value = "SELECT nextval('seq_purge_no')", nativeQuery = true)
    long nextPurgeSeq();
}
