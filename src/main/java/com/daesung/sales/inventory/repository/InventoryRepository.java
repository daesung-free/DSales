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
     * 원자적 잔량 증감(입고/도착 +). {@code qty = qty + delta}를 DB 한 문장으로 처리(lost update 없음).
     * 반환값 = 영향 행수(0이면 행 없음 → 신규 생성 필요).
     */
    @Modifying(clearAutomatically = true)
    @Query("update Inventory i set i.qty = i.qty + :delta "
            + "where i.product.id = :productId and i.warehouse.id = :warehouseId")
    int addQty(@Param("productId") Long productId,
               @Param("warehouseId") Long warehouseId,
               @Param("delta") int delta);

    /**
     * 음수재고 방지 원자적 차감/증감. {@code qty + delta >= 0}일 때만 갱신.
     * 반환값 = 영향 행수(0이면 행 없음 또는 재고 부족 → 호출부에서 예외).
     */
    @Modifying(clearAutomatically = true)
    @Query("update Inventory i set i.qty = i.qty + :delta "
            + "where i.product.id = :productId and i.warehouse.id = :warehouseId and i.qty + :delta >= 0")
    int addQtyIfEnough(@Param("productId") Long productId,
                       @Param("warehouseId") Long warehouseId,
                       @Param("delta") int delta);

    /** 창고의 재고 총합. 창고를 중지해도 되는지 판단하는 데 쓴다(0이어야 안전). */
    @org.springframework.data.jpa.repository.Query(
            "select coalesce(sum(i.qty), 0) from Inventory i where i.warehouse.id = :warehouseId")
    int totalQtyByWarehouse(@org.springframework.data.repository.query.Param("warehouseId") Long warehouseId);
}
