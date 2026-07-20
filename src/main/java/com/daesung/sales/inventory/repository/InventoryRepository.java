package com.daesung.sales.inventory.repository;

import com.daesung.sales.inventory.entity.Inventory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    /**
     * 원자적 잔량 증감(입고 +, 출고 -). {@code qty = qty + delta}를 DB에서 한 문장으로 처리하므로
     * 동시 갱신에도 lost update가 없다(DB 행 잠금으로 직렬화). 반환값 = 영향 행수(0이면 행 없음 → 신규 생성 필요).
     */
    @Modifying(clearAutomatically = true)
    @Query("update Inventory i set i.qty = i.qty + :delta "
            + "where i.product.id = :productId and i.warehouse.id = :warehouseId")
    int addQty(@Param("productId") Long productId,
               @Param("warehouseId") Long warehouseId,
               @Param("delta") int delta);
}
