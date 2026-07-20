package com.daesung.sales.inventory.repository;

import com.daesung.sales.inventory.entity.InventoryTxn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTxnRepository extends JpaRepository<InventoryTxn, Long> {
    List<InventoryTxn> findByProductIdAndWarehouseId(Long productId, Long warehouseId);
}
