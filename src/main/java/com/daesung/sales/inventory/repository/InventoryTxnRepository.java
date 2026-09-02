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

    /**
     * 상품별 <b>매입 입고수량</b>(기간). 근거: 정본 16p 데이터 항목 "입고/매입반품/매입액".
     *
     * <p>★단가(위 가중평균)와 달리 이것은 <b>기간으로 자른다</b>. 화면이 묻는 것은
     * "이 기간에 얼마나 들여왔나"이고, 단가는 "지금까지 평균 얼마에 들여왔나"라서
     * 자르는 기준이 다르다. 같은 기준으로 맞추면 기간 내 입고가 없는 달에 단가가 0이 되어
     * 이익이 매출 전액으로 부풀어 오른다.
     *
     * <p>반환 Object[]: [productId, inboundQty, inboundAmount].
     */
    @Query(value = """
            SELECT product_id, COALESCE(SUM(qty), 0), COALESCE(SUM(qty * COALESCE(unit_cost, 0)), 0)
            FROM inventory_txn
            WHERE txn_type = 'INBOUND' AND inbound_type = 'PURCHASE'
              AND trade_date BETWEEN :fromDate AND :toDate
            GROUP BY product_id
            """, nativeQuery = true)
    List<Object[]> purchaseInboundByProduct(@Param("fromDate") LocalDate fromDate,
                                            @Param("toDate") LocalDate toDate);

    /**
     * 폐기 내역(10p 조회). 재고이벤트 DISPOSE를 최근순으로.
     *
     * <p>★별도 폐기 테이블을 만들지 않는다 — 재고는 {@code inventory_txn}이 유일 진실이고
     * 폐기 원장을 따로 두면 둘이 어긋날 수 있다(§재고 정의 단일화).
     *
     * <p>수량은 원장에 <b>음수</b>로 들어 있다(재고를 깎으므로). 화면에는 양수로 보여야 하니
     * 부호는 서비스에서 뒤집는다 — 쿼리에서 뒤집으면 "원장 값"과 "보이는 값"이
     * 어디서 갈렸는지 추적이 어려워진다.
     */
    @Query("""
            select t from InventoryTxn t
              join fetch t.product join fetch t.warehouse
             where t.txnType = com.daesung.sales.inventory.entity.TxnType.DISPOSE
               and (cast(:fromDate as date) is null or t.tradeDate >= :fromDate)
               and (cast(:toDate as date) is null or t.tradeDate <= :toDate)
               and (:anyProduct = true or t.product.id in :productIds)
               and (:anyWarehouse = true or t.warehouse.id in :warehouseIds)
             order by t.tradeDate desc, t.id desc
            """)
    List<InventoryTxn> findDisposals(@Param("fromDate") LocalDate fromDate,
                                     @Param("toDate") LocalDate toDate,
                                     @Param("anyProduct") boolean anyProduct,
                                     @Param("productIds") java.util.Collection<Long> productIds,
                                     @Param("anyWarehouse") boolean anyWarehouse,
                                     @Param("warehouseIds") java.util.Collection<Long> warehouseIds);

    /**
     * 입고/대체 내역(8p·9p 조회). 재고를 움직이는 <b>입고·이고·세트작업</b>만 최근순으로.
     *
     * <p>★출고·폐기·반품·실사는 뺀다 — 그건 각자 화면이 따로 있다(매출조회·폐기조회·수불부).
     * 한 목록에 다 담으면 "입고/대체 등록" 화면인데 매출까지 섞여 보인다.
     *
     * <p>{@code kinds}가 비어 있으면 네 종류 전부. 화면의 작업모드 탭(일반입고/단순이고/세트)에 대응.
     */
    @Query("""
            select t from InventoryTxn t
              join fetch t.product join fetch t.warehouse
             where t.txnType in :kinds
               and (cast(:fromDate as date) is null or t.tradeDate >= :fromDate)
               and (cast(:toDate as date) is null or t.tradeDate <= :toDate)
               and (:anyProduct = true or t.product.id in :productIds)
               and (:anyWarehouse = true or t.warehouse.id in :warehouseIds)
             order by t.tradeDate desc, t.id desc
            """)
    List<InventoryTxn> findStockRecords(@Param("kinds") java.util.Collection<com.daesung.sales.inventory.entity.TxnType> kinds,
                                        @Param("fromDate") LocalDate fromDate,
                                        @Param("toDate") LocalDate toDate,
                                        @Param("anyProduct") boolean anyProduct,
                                        @Param("productIds") java.util.Collection<Long> productIds,
                                        @Param("anyWarehouse") boolean anyWarehouse,
                                        @Param("warehouseIds") java.util.Collection<Long> warehouseIds);

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
              AND (:warehouseType IS NULL OR w.type = :warehouseType)
              AND p.ledger_visible = TRUE
            GROUP BY t.product_id, p.code, p.name, t.warehouse_id, w.name
            ORDER BY p.code, w.name
            """, nativeQuery = true)
    List<Object[]> stockLedger(@Param("fromDate") LocalDate fromDate,
                               @Param("toDate") LocalDate toDate,
                               @Param("productId") Long productId,
                               @Param("warehouseId") Long warehouseId,
                               @Param("warehouseType") String warehouseType);

    /**
     * 제품수불부 <b>결산내역</b> 집계(분류×상품, 창고 합산). 근거: 정본 11p "결산내역(연초~기준일 누적)" +
     * 레거시 제품수불부 「결산내역」 체크박스.
     *
     * <p>버킷 구성은 {@link #stockLedger}와 같다. 다른 것은 두 가지뿐이다 —
     * <b>창고로 나누지 않고</b>(레거시엔 창고 축이 없다) <b>분류코드로 묶는다</b>(rollup 대상).
     * 창고구분 필터는 남긴다(2026-08-21 회신 「위탁 미결잔여가 어느 창고에 있는지」).
     *
     * <p>기간의 시작(연초)은 호출부가 정한다 — 이 쿼리는 받은 fromDate를 그대로 쓴다.
     *
     * <p>반환 Object[]: [catCode, catName, productId, code, name,
     *   opening, inbound, transfer, bom, dispose, sale, free, teacher, salesReturn, adjust, closing].
     */
    @Query(value = """
            SELECT p.cat_code, p.cat_name, t.product_id, p.code, p.name,
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
              COALESCE(SUM(CASE WHEN t.trade_date <= :toDate THEN t.qty ELSE 0 END), 0) AS closing
            FROM inventory_txn t
              JOIN products p ON p.id = t.product_id
              JOIN warehouses w ON w.id = t.warehouse_id
            WHERE (CAST(:productId AS SIGNED) IS NULL OR t.product_id = :productId)
              AND (:warehouseType IS NULL OR w.type = :warehouseType)
              AND p.ledger_visible = TRUE
            GROUP BY p.cat_code, p.cat_name, t.product_id, p.code, p.name
            ORDER BY p.cat_code, p.code
            """, nativeQuery = true)
    List<Object[]> stockSettlement(@Param("fromDate") LocalDate fromDate,
                                   @Param("toDate") LocalDate toDate,
                                   @Param("productId") Long productId,
                                   @Param("warehouseType") String warehouseType);

    /**
     * 폐기 <b>분류명별 요약</b>(10p). 근거: 발주처 화면검토(2026-08-31) —
     * "폐기 내역 조회시에도 <b>분류명 별로</b> 해당 내역 요약(전체)/상세가 모두 조회 가능한지".
     *
     * <p>상세({@link #findDisposals})가 낱건이라면 이건 분류 단위 합계다.
     * 수량은 원장에 음수로 들어 있어 양수로 뒤집는다 — "몇 부 버렸나"에 음수를 주면
     * 읽는 사람이 매번 뒤집어야 한다.
     *
     * <p>반환 Object[]: [catCode, catName, 폐기건수, 폐기수량].
     *
     * <p>★필터는 {@link #findDisposals}(상세)와 <b>같은 축·같은 규칙</b>이어야 한다.
     * 한쪽만 다중선택이 되면 담당자가 창고 둘을 고른 순간 요약과 상세의 수량이 갈리고,
     * 그러면 <b>둘 다 못 믿는다</b>.
     */
    @Query(value = """
            SELECT p.cat_code, p.cat_name, COUNT(*) AS cnt, COALESCE(SUM(-t.qty), 0) AS qty
            FROM inventory_txn t JOIN products p ON p.id = t.product_id
            WHERE t.txn_type = 'DISPOSE'
              AND (CAST(:fromDate AS DATE) IS NULL OR t.trade_date >= :fromDate)
              AND (CAST(:toDate   AS DATE) IS NULL OR t.trade_date <= :toDate)
              AND (:anyProduct = TRUE OR t.product_id IN (:productIds))
              AND (:anyWarehouse = TRUE OR t.warehouse_id IN (:warehouseIds))
            GROUP BY p.cat_code, p.cat_name
            ORDER BY p.cat_code
            """, nativeQuery = true)
    List<Object[]> disposalSummaryByCategory(@Param("fromDate") LocalDate fromDate,
                                             @Param("toDate") LocalDate toDate,
                                             @Param("anyProduct") boolean anyProduct,
                                             @Param("productIds") java.util.Collection<Long> productIds,
                                             @Param("anyWarehouse") boolean anyWarehouse,
                                             @Param("warehouseIds") java.util.Collection<Long> warehouseIds);

    /**
     * 상품별 <b>소요 기준 출고량</b>(자재 상세용). 근거: 발주처 구조보완요청안(2026-08-31)
     * "세트 출고분·회차 단독 출고분은 각각 세트·회차의 <b>매출+교사용 합계</b>(실제 출고된 수량)".
     *
     * <p>같은 문서의 소요량 반영 기준을 그대로 담았다 —
     * <ul>
     *   <li><b>폐기</b> → 매출·교사용과 동일하게 <b>합산</b></li>
     *   <li><b>반품</b> → 위탁출고 반품과 동일하게 <b>차감</b></li>
     *   <li><b>대체</b> → 세트조립·해체만 반영(조립 시 소진·해체 시 복원),
     *       <b>창고이동은 무관</b> — 창고만 옮긴 것은 소비가 아니다</li>
     * </ul>
     *
     * <p>부호를 양수로 뒤집어 낸다. 원장은 출고를 음수로 적지만 "몇 부 나갔나"를 묻는
     * 화면에 음수를 주면 읽는 사람이 매번 뒤집어야 한다.
     *
     * <p>증정(GIFT)도 포함한다 — 문서 문구는 "매출+교사용"이지만 증정도 실제로 물건이 나가고,
     * 정본 수불부 컬럼에 증정 칸이 따로 없어 교사용·무상으로 묶여 있다.
     *
     * <p>반환 Object[]: [productId, 소요기준 출고량].
     */
    @Query(value = """
            SELECT product_id,
              COALESCE(SUM(CASE WHEN shipment_type IN ('NORMAL_SHIP','GIFT','TEACHER_USE') THEN -qty ELSE 0 END), 0)
              + COALESCE(SUM(CASE WHEN txn_type = 'DISPOSE' THEN -qty ELSE 0 END), 0)
              - COALESCE(SUM(CASE WHEN shipment_type = 'RETURN' THEN qty ELSE 0 END), 0)
              + COALESCE(SUM(CASE WHEN txn_type IN ('BOM_ASSEMBLE','BOM_DISASSEMBLE') THEN qty ELSE 0 END), 0)
                AS consumed
            FROM inventory_txn
            WHERE product_id IN (:productIds)
              AND (CAST(:fromDate AS DATE) IS NULL OR trade_date >= :fromDate)
              AND (CAST(:toDate   AS DATE) IS NULL OR trade_date <= :toDate)
            GROUP BY product_id
            """, nativeQuery = true)
    List<Object[]> consumedQtyByProduct(@Param("productIds") java.util.Collection<Long> productIds,
                                        @Param("fromDate") LocalDate fromDate,
                                        @Param("toDate") LocalDate toDate);

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
