package com.daesung.sales.inventory.repository;

import com.daesung.sales.inventory.entity.InventoryTxn;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryTxnRepository extends JpaRepository<InventoryTxn, Long> {
    List<InventoryTxn> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    /** 폐기번호(P) 채번용 시퀀스. */
    @Query(value = "SELECT nextval('seq_purge_no')", nativeQuery = true)
    long nextPurgeSeq();

    /**
     * 제품수불부 집계(상품×창고). 물류 이벤트를 CASE 버킷으로 합산 + 이월/마감.
     * 출고는 shipment_type으로 매출/무상/교사용/반품 분해(스펙 수불부 컬럼 대응).
     * 반환 Object[]: [productId, code, name, warehouseId, whName,
     *   opening, inbound, transfer, bom, dispose, sale, free, teacher, salesReturn, closing, cached]
     */
    @Query(value = """
            SELECT t.product_id, p.code, p.name, t.warehouse_id, w.name,
              COALESCE(SUM(CASE WHEN t.trade_date < :fromDate THEN t.qty ELSE 0 END), 0) AS opening,
              COALESCE(SUM(CASE WHEN t.trade_date BETWEEN :fromDate AND :toDate AND t.txn_type = 'INBOUND' THEN t.qty ELSE 0 END), 0) AS inbound,
              COALESCE(SUM(CASE WHEN t.trade_date BETWEEN :fromDate AND :toDate AND t.txn_type = 'TRANSFER' THEN t.qty ELSE 0 END), 0) AS transfer,
              COALESCE(SUM(CASE WHEN t.trade_date BETWEEN :fromDate AND :toDate AND t.txn_type IN ('BOM_ASSEMBLE','BOM_DISASSEMBLE') THEN t.qty ELSE 0 END), 0) AS bom,
              COALESCE(SUM(CASE WHEN t.trade_date BETWEEN :fromDate AND :toDate AND t.txn_type = 'DISPOSE' THEN t.qty ELSE 0 END), 0) AS dispose,
              COALESCE(SUM(CASE WHEN t.trade_date BETWEEN :fromDate AND :toDate AND t.shipment_type = 'NORMAL_SHIP' THEN t.qty ELSE 0 END), 0) AS sale,
              COALESCE(SUM(CASE WHEN t.trade_date BETWEEN :fromDate AND :toDate AND t.shipment_type = 'GIFT' THEN t.qty ELSE 0 END), 0) AS free,
              COALESCE(SUM(CASE WHEN t.trade_date BETWEEN :fromDate AND :toDate AND t.shipment_type = 'TEACHER_USE' THEN t.qty ELSE 0 END), 0) AS teacher,
              COALESCE(SUM(CASE WHEN t.trade_date BETWEEN :fromDate AND :toDate AND t.shipment_type = 'RETURN' THEN t.qty ELSE 0 END), 0) AS sales_return,
              COALESCE(SUM(CASE WHEN t.trade_date <= :toDate THEN t.qty ELSE 0 END), 0) AS closing,
              COALESCE(MAX(inv.qty), 0) AS cached
            FROM inventory_txn t
              JOIN products p ON p.id = t.product_id
              JOIN warehouses w ON w.id = t.warehouse_id
              LEFT JOIN inventory inv ON inv.product_id = t.product_id AND inv.warehouse_id = t.warehouse_id
            WHERE (CAST(:productId AS bigint) IS NULL OR t.product_id = :productId)
              AND (CAST(:warehouseId AS bigint) IS NULL OR t.warehouse_id = :warehouseId)
            GROUP BY t.product_id, p.code, p.name, t.warehouse_id, w.name
            ORDER BY p.code, w.name
            """, nativeQuery = true)
    List<Object[]> stockLedger(@Param("fromDate") LocalDate fromDate,
                               @Param("toDate") LocalDate toDate,
                               @Param("productId") Long productId,
                               @Param("warehouseId") Long warehouseId);
}
