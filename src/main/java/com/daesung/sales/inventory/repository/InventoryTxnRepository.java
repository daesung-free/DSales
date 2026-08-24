package com.daesung.sales.inventory.repository;

import com.daesung.sales.inventory.entity.InventoryTxn;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryTxnRepository extends JpaRepository<InventoryTxn, Long> {
    List<InventoryTxn> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    /**
     * 상품별 매입원가(매입입고 unit_cost 가중평균 = Σ(qty×unit_cost)/Σqty). 외부콘텐츠 이익 계산용.
     * 근거: 요구사항 8p 정정(2026-07-28) — PURCHASE(매입입고)로 등록된 입고만 매입 데이터로 집계.
     * 반환 Object[]: [productId, avgCost].
     */
    @Query(value = """
            SELECT product_id, COALESCE(SUM(qty * unit_cost) / NULLIF(SUM(qty), 0), 0) AS avg_cost
            FROM inventory_txn
            WHERE txn_type = 'INBOUND' AND inbound_type = 'PURCHASE' AND unit_cost IS NOT NULL
            GROUP BY product_id
            """, nativeQuery = true)
    List<Object[]> avgInboundCostByProduct();

    /** 특정 전표(refNo)로 생성된 출고/반품 이벤트(매출취소 역분개용). product·warehouse 즉시 로드. */
    @Query("select t from InventoryTxn t join fetch t.product join fetch t.warehouse"
            + " where t.refNo = :refNo and t.shipmentType is not null")
    List<InventoryTxn> findShipmentsByRefNo(String refNo);

    /** 이고 도착다리 조회(위탁 반품 역-자동이고용). sourceTxn=출발다리인 도착 이벤트 → 위탁창고. */
    @Query("select t from InventoryTxn t join fetch t.warehouse where t.sourceTxn.id = :sourceTxnId")
    java.util.Optional<InventoryTxn> findBySourceTxnId(Long sourceTxnId);

    /**
     * 제품수불부 집계(상품×창고). 물류 이벤트를 CASE 버킷으로 합산 + 이월/마감.
     * 출고는 shipment_type으로 매출/무상/교사용/반품 분해(스펙 수불부 컬럼 대응).
     * 반환 Object[]: [productId, code, name, warehouseId, whName,
     *   opening, inbound, transfer, bom, dispose, sale, free, teacher, salesReturn, adjust, closing, cached]
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
              COALESCE(SUM(CASE WHEN t.trade_date BETWEEN :fromDate AND :toDate AND t.txn_type = 'ADJUST' THEN t.qty ELSE 0 END), 0) AS adjust,
              COALESCE(SUM(CASE WHEN t.trade_date <= :toDate THEN t.qty ELSE 0 END), 0) AS closing,
              COALESCE(MAX(inv.qty), 0) AS cached
            FROM inventory_txn t
              JOIN products p ON p.id = t.product_id
              JOIN warehouses w ON w.id = t.warehouse_id
              LEFT JOIN inventory inv ON inv.product_id = t.product_id AND inv.warehouse_id = t.warehouse_id
            WHERE (CAST(:productId AS SIGNED) IS NULL OR t.product_id = :productId)
              AND (CAST(:warehouseId AS SIGNED) IS NULL OR t.warehouse_id = :warehouseId)
              AND p.ledger_visible = TRUE
            GROUP BY t.product_id, p.code, p.name, t.warehouse_id, w.name
            ORDER BY p.code, w.name
            """, nativeQuery = true)
    List<Object[]> stockLedger(@Param("fromDate") LocalDate fromDate,
                               @Param("toDate") LocalDate toDate,
                               @Param("productId") Long productId,
                               @Param("warehouseId") Long warehouseId);

    /**
     * 도서입출고현황의 매입측(상품별 입고/취소) + 재고. 근거: 레거시 도서입출고현황.vb 입고/취소 버킷.
     * 입고=INBOUND qty&gt;0(원가금액=qty×unit_cost), 취소(매입취소)=INBOUND qty&lt;0(역분개), 재고=SUM(qty≤종료일)(정본 재고).
     * 상품 전 창고 합산. 반환 Object[]: [productId, code, name, catCode, catName, price,
     *   inboundQty, inboundAmt, cancelQty, cancelAmt, stockQty].
     */
    @Query(value = """
            SELECT t.product_id, p.code, p.name, p.cat_code, p.cat_name, p.price,
              COALESCE(SUM(CASE WHEN t.trade_date BETWEEN :fromDate AND :toDate AND t.txn_type='INBOUND' AND t.qty>0 THEN t.qty ELSE 0 END),0) AS inbound_qty,
              COALESCE(SUM(CASE WHEN t.trade_date BETWEEN :fromDate AND :toDate AND t.txn_type='INBOUND' AND t.qty>0 THEN t.qty*COALESCE(t.unit_cost,0) ELSE 0 END),0) AS inbound_amt,
              COALESCE(SUM(CASE WHEN t.trade_date BETWEEN :fromDate AND :toDate AND t.txn_type='INBOUND' AND t.qty<0 THEN -t.qty ELSE 0 END),0) AS cancel_qty,
              COALESCE(SUM(CASE WHEN t.trade_date BETWEEN :fromDate AND :toDate AND t.txn_type='INBOUND' AND t.qty<0 THEN -t.qty*COALESCE(t.unit_cost,0) ELSE 0 END),0) AS cancel_amt,
              COALESCE(SUM(CASE WHEN t.trade_date <= :toDate THEN t.qty ELSE 0 END),0) AS stock_qty
            FROM inventory_txn t JOIN products p ON p.id = t.product_id
            WHERE (CAST(:catCode AS CHAR) IS NULL OR p.cat_code = :catCode)
              AND (CAST(:productId AS SIGNED) IS NULL OR t.product_id = :productId)
            GROUP BY t.product_id, p.code, p.name, p.cat_code, p.cat_name, p.price
            ORDER BY p.code
            """, nativeQuery = true)
    List<Object[]> bookPurchaseAgg(@Param("fromDate") LocalDate fromDate,
                                   @Param("toDate") LocalDate toDate,
                                   @Param("catCode") String catCode,
                                   @Param("productId") Long productId);

}
