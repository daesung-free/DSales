package com.daesung.sales.inventory.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.query.MultiSelect;
import com.daesung.sales.inventory.dto.BomWorkRequest;
import com.daesung.sales.inventory.dto.BomWorkResponse;
import com.daesung.sales.inventory.dto.DisposalRequest;
import com.daesung.sales.inventory.dto.BomWorkStatusResponse;
import com.daesung.sales.inventory.dto.DisposalRecordRow;
import com.daesung.sales.inventory.dto.DisposalSummaryResponse;
import com.daesung.sales.inventory.dto.DisposalResponse;
import com.daesung.sales.inventory.dto.InboundRequest;
import com.daesung.sales.inventory.dto.InboundResponse;
import com.daesung.sales.inventory.entity.InboundType;
import com.daesung.sales.inventory.dto.StockLedgerRow;
import com.daesung.sales.inventory.dto.StockRecordRow;
import com.daesung.sales.inventory.dto.StockSettlementRow;
import com.daesung.sales.warehouse.entity.WarehouseType;
import com.daesung.sales.inventory.dto.TransferRequest;
import com.daesung.sales.inventory.dto.TransferResponse;
import com.daesung.sales.inventory.entity.BomDirection;
import com.daesung.sales.inventory.entity.Inventory;
import com.daesung.sales.inventory.entity.InventoryTxn;
import com.daesung.sales.inventory.entity.TxnType;
import com.daesung.sales.inventory.entity.VoucherCancel;
import com.daesung.sales.inventory.repository.InventoryRepository;
import com.daesung.sales.inventory.repository.InventoryTxnRepository;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.BomItem;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.BomItemRepository;
import com.daesung.sales.product.repository.ProductRepository;
import com.daesung.sales.salestype.entity.ShipmentType;
import com.daesung.sales.warehouse.entity.Warehouse;
import com.daesung.sales.warehouse.repository.WarehouseRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InventoryService.class);

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final InventoryRepository inventoryRepository;
    private final SequenceService sequenceService;
    private final InventoryTxnRepository inventoryTxnRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final PartnerRepository partnerRepository;
    private final BomItemRepository bomItemRepository;
    private final com.daesung.sales.inventory.config.InventoryProperties inventoryProperties;
    private final StockWarningCollector stockWarningCollector;
    private final com.daesung.sales.inventory.repository.VoucherCancelRepository voucherCancelRepository;
    private final com.daesung.sales.closing.service.PeriodLockService periodLockService;

    /** 일반 입고. 품목마다 (1) 재고이벤트 INBOUND 기록 + (2) 재고 잔량 가산을 한 트랜잭션으로. */
    @Transactional
    public InboundResponse inbound(InboundRequest req) {
        Warehouse warehouse = warehouseRepository.findById(req.destinationWarehouseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "창고가 없습니다. id=" + req.destinationWarehouseId()));
        Partner supplier = partnerRepository.findById(req.supplierClientId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + req.supplierClientId()));

        // ★전표번호를 붙인다. 예전엔 입고에만 없어 "무엇을 되돌릴지" 특정할 수 없었다(취소 신설).
        String inboundNo = "IN-" + req.processedDate().format(YYYYMMDD) + "-"
                + sequenceService.next(SequenceService.SEQ_INBOUND);

        List<InboundResponse.Line> lines = new ArrayList<>();
        for (InboundRequest.InboundItem item : req.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "상품이 없습니다. id=" + item.productId()));

            InventoryTxn txn = InventoryTxn.inbound(product, warehouse, item.qty(),
                    item.unitCost(), req.inboundType(), req.processedDate(), supplier, item.memo(), inboundNo);
            txn.applyLogisCostTarget(Boolean.TRUE.equals(req.logisCostTarget()));   // 물류작업비 대상(8p)
            inventoryTxnRepository.save(txn);
            int currentQty = applyDelta(product, warehouse, item.qty());

            lines.add(new InboundResponse.Line(
                    product.getId(), product.getCode(), item.qty(), currentQty));
        }
        InboundType type = (req.inboundType() != null) ? req.inboundType() : InboundType.NORMAL;
        return new InboundResponse(inboundNo, warehouse.getId(), warehouse.getName(), type, lines);
    }

    /** 단순 이고(창고 이동). 출발창고 −qty(음수재고 방지), 도착창고 +qty. 매출 미발생. */
    @Transactional
    public TransferResponse transfer(TransferRequest req) {
        if (req.fromWarehouseId().equals(req.toWarehouseId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "출발/도착 창고가 같습니다.");
        }
        Warehouse from = warehouseRepository.findById(req.fromWarehouseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "출발 창고가 없습니다. id=" + req.fromWarehouseId()));
        Warehouse to = warehouseRepository.findById(req.toWarehouseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "도착 창고가 없습니다. id=" + req.toWarehouseId()));

        List<TransferResponse.Line> lines = new ArrayList<>();
        for (TransferRequest.Item item : req.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "상품이 없습니다. id=" + item.productId()));
            moveStock(product, from, to, item.qty(), req.processedDate(), item.reason());
            lines.add(new TransferResponse.Line(product.getId(), product.getCode(), item.qty(),
                    balanceOf(product.getId(), from.getId()), balanceOf(product.getId(), to.getId())));
        }
        return new TransferResponse(from.getId(), from.getName(), to.getId(), to.getName(), lines,
                stockWarningCollector.drain());
    }

    /**
     * 재고 이동 1건(출발 −qty 음수방지, 도착 +qty) + 재고이벤트 2다리. 이고·위탁출고 공용 빌딩블록.
     * 반환값 = 출발다리 이벤트(위탁출고의 origin_txn 링크에 사용). 반드시 호출자 트랜잭션 내에서.
     */
    public InventoryTxn moveStock(Product product, Warehouse from, Warehouse to, int qty,
                                  LocalDate tradeDate, String reason) {
        applyDelta(product, from, -qty);
        InventoryTxn outLeg = inventoryTxnRepository.save(
                InventoryTxn.transfer(product, from, -qty, tradeDate, null, reason));
        applyDelta(product, to, qty);
        inventoryTxnRepository.save(
                InventoryTxn.transfer(product, to, qty, tradeDate, outLeg, reason));
        return outLeg;
    }

    /**
     * 출고/반품 재고 반영 + 이벤트 1건. 정상출고=OUTBOUND(delta 음수, 음수재고 방지), 반품=RETURN(delta 양수).
     * shipmentType을 이벤트에 태그해 수불부가 매출/무상/교사용/반품으로 분해. 반환값 = 갱신 후 잔량.
     * 반드시 호출자 트랜잭션 내에서(매출등록과 한 트랜잭션).
     */
    public int applyShipment(Product product, Warehouse warehouse, int delta, TxnType txnType,
                             ShipmentType shipmentType, LocalDate tradeDate, String refNo, String memo) {
        int balance = applyDelta(product, warehouse, delta);
        inventoryTxnRepository.save(
                InventoryTxn.shipment(product, warehouse, delta, txnType, shipmentType, tradeDate, refNo, memo));
        return balance;
    }

    /**
     * 매출취소 역분개. refNo(매출번호)로 생성된 출고/반품 이벤트를 찾아, 같은 버킷에 반대 부호로 되돌린다.
     * 재고 복구(반대 delta) + 상쇄 이벤트 생성 → 수불부 버킷도 상쇄(net 0). 원출고 없으면(위탁정산 등) no-op.
     * 반드시 호출자 트랜잭션 내에서. 반품 취소로 차감 시 음수재고면 NEGATIVE_STOCK.
     */
    public void reverseShipments(String refNo, LocalDate reverseDate) {
        for (InventoryTxn origin : inventoryTxnRepository.findShipmentsByRefNo(refNo)) {
            int reverseDelta = -origin.getQty();
            applyDelta(origin.getProduct(), origin.getWarehouse(), reverseDelta);
            inventoryTxnRepository.save(InventoryTxn.shipment(
                    origin.getProduct(), origin.getWarehouse(), reverseDelta,
                    origin.getTxnType(), origin.getShipmentType(), reverseDate, refNo,
                    "매출취소 역분개: " + refNo));
        }
    }

    /**
     * 폐기·입고 전표 취소. 물리 삭제가 아니라 <b>역분개 + 취소 이력</b>이다.
     *
     * <p>근거: 발주처 회신 「삭제권한」 — "마감 확정 전에는 잘못 등록한 건을 삭제할 수 있어야.
     * 확정 후에는 물리 삭제 없이 취소 처리로". 재고는 이벤트 로그가 유일 진실이라
     * 확정 전후와 무관하게 <b>지우지 않고 되돌린다</b> — 지우면 이력이 사라진다.
     *
     * <p>‼️<b>마감된 달은 막는다.</b> 되돌리면 그 달 재고·수불부가 바뀌는데,
     * 마감은 "이 달 숫자를 더 안 건드린다"는 선언이다.
     *
     * @param refNo 폐기 {@code P-…} / 입고 {@code IN-…}
     */
    @Transactional
    public com.daesung.sales.inventory.dto.VoucherCancelResponse cancelVoucher(String refNo, String reason) {
        if (voucherCancelRepository.existsByRefNo(refNo)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 취소된 전표입니다: " + refNo);
        }
        List<InventoryTxn> origins = inventoryTxnRepository.findAllByRefNo(refNo);
        if (origins.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "전표가 없습니다: " + refNo);
        }
        // 원 전표의 거래일 기준으로 마감을 본다 — 되돌리는 대상이 그 달의 숫자다.
        periodLockService.assertNotLocked(origins.get(0).getTradeDate());

        String kind = origins.get(0).getTxnType() == TxnType.INBOUND ? "INBOUND" : "DISPOSE";
        int n = reverseByRefNo(refNo, LocalDate.now(),
                (kind.equals("INBOUND") ? "입고취소" : "폐기취소") + " 역분개: " + refNo);
        voucherCancelRepository.save(VoucherCancel.of(refNo, kind, reason, n));
        return new com.daesung.sales.inventory.dto.VoucherCancelResponse(
                refNo, kind, n, stockWarningCollector.drain());
    }

    /**
     * 전표(refNo)의 재고 이벤트를 <b>통째로</b> 되돌린다. 폐기·입고 취소용.
     *
     * <p>★<b>물리 삭제하지 않는다.</b> 반대 부호 이벤트를 새로 적어 상쇄한다 —
     * 재고는 이벤트 로그가 유일 진실이라, 지우면 "언제 왜 되돌렸나"가 사라진다.
     * 매출취소({@link #reverseShipments})와 같은 규율이다.
     *
     * <p>‼️이미 되돌린 전표를 또 되돌리면 재고가 반대로 밀린다. 중복 취소는 호출부가 막는다
     * (폐기·입고 엔티티의 canceled 플래그).
     *
     * @return 되돌린 이벤트 수. 0이면 그 전표로 만들어진 재고 이벤트가 없다는 뜻이다
     *         (재고 미관리 상품만 있던 전표 등) — 오류가 아니다.
     */
    @Transactional
    public int reverseByRefNo(String refNo, LocalDate reverseDate, String memo) {
        int n = 0;
        for (InventoryTxn origin : inventoryTxnRepository.findAllByRefNo(refNo)) {
            int reverseDelta = -origin.getQty();
            applyDelta(origin.getProduct(), origin.getWarehouse(), reverseDelta);
            inventoryTxnRepository.save(InventoryTxn.shipment(
                    origin.getProduct(), origin.getWarehouse(), reverseDelta,
                    origin.getTxnType(), origin.getShipmentType(), reverseDate, refNo, memo));
            n++;
        }
        return n;
    }

    /**
     * 재고실사 조정. delta=실물−시스템(부호 포함). 캐시 갱신 + ADJUST 이벤트. 반환값=조정 후 잔량(=실물수량).
     * 반드시 호출자 트랜잭션 내에서(실사 등록과 한 트랜잭션).
     */
    public int adjust(Product product, Warehouse warehouse, int delta,
                      LocalDate tradeDate, String refNo, String memo) {
        int balance = applyDelta(product, warehouse, delta);
        inventoryTxnRepository.save(InventoryTxn.adjust(product, warehouse, delta, tradeDate, refNo, memo));
        return balance;
    }

    /** 상품×창고 현재 잔량(캐시). 없으면 0. */
    public int balanceOf(Long productId, Long warehouseId) {
        return inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .map(Inventory::getQty)
                .orElse(0);
    }

    /**
     * BOM 조립/해체. 구성품·비율은 상품 BOM 마스터에서 읽는다.
     * 조립: 완제품 +workQty / 구성품 각 −(비율×workQty). 해체: 반대. 전체 한 트랜잭션.
     */
    @Transactional
    public BomWorkResponse bom(BomWorkRequest req) {
        Warehouse warehouse = warehouseRepository.findById(req.warehouseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "창고가 없습니다. id=" + req.warehouseId()));
        Product parent = productRepository.findById(req.parentProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "완제품 상품이 없습니다. id=" + req.parentProductId()));
        List<BomItem> boms = bomItemRepository.findByParentId(parent.getId());
        if (boms.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "BOM 구성이 없습니다: " + parent.getCode());
        }

        boolean assemble = req.direction() == BomDirection.ASSEMBLE;
        TxnType txnType = assemble ? TxnType.BOM_ASSEMBLE : TxnType.BOM_DISASSEMBLE;

        // ⚠️조립 시점 작업비 계산은 없다(발주처 회신 2026-08-21 철회) —
        //   "조립·해체 시점에는 별도 정산하지 않고, 실제 출고되는 수량만큼만
        //    출고 시점에 출고 작업비에 포함해 청구하는 현행 방식으로 운영".

        // 완제품: 조립 +, 해체 −
        int parentDelta = assemble ? req.workQty() : -req.workQty();
        int parentBal = applyDelta(parent, warehouse, parentDelta);
        InventoryTxn parentTxn = InventoryTxn.bom(
                parent, warehouse, parentDelta, txnType, req.processedDate(), req.memo());
        inventoryTxnRepository.save(parentTxn);
        // 완제품 행: 비율·자재구분은 구성품에만 있는 값이라 비운다.
        BomWorkResponse.Line parentLine = new BomWorkResponse.Line(
                parent.getId(), parent.getCode(), parent.getName(),
                null, null, parentDelta, parentBal);

        // 구성품: 조립 −(비율×수량), 해체 +(비율×수량)
        List<BomWorkResponse.Line> compLines = new ArrayList<>();
        for (BomItem b : boms) {
            Product child = b.getChild();
            int compDelta = (assemble ? -1 : 1) * b.getRatio() * req.workQty();
            int compBal = applyDelta(child, warehouse, compDelta);
            inventoryTxnRepository.save(InventoryTxn.bom(child, warehouse, compDelta, txnType, req.processedDate(), req.memo()));
            compLines.add(new BomWorkResponse.Line(child.getId(), child.getCode(), child.getName(),
                    b.getRatio(),
                    (b.getMaterialType() == null) ? null : b.getMaterialType().name(),
                    compDelta, compBal));
        }

        return new BomWorkResponse(warehouse.getId(), warehouse.getName(), req.direction(),
                parentLine, req.workQty(), compLines, stockWarningCollector.drain());
    }


    /** 폐기. 품목마다 재고 즉시 차감(음수재고 방지) + DISPOSE 이벤트. 폐기번호(P) 채번. 한 트랜잭션. */
    @Transactional
    public DisposalResponse dispose(DisposalRequest req) {
        Warehouse warehouse = warehouseRepository.findById(req.warehouseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "창고가 없습니다. id=" + req.warehouseId()));
        String disposalNo = "P-" + req.processedDate().format(YYYYMMDD) + "-"
                + sequenceService.next(SequenceService.SEQ_PURGE);

        List<DisposalResponse.Line> lines = new ArrayList<>();
        for (DisposalRequest.Item item : req.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "상품이 없습니다. id=" + item.productId()));
            int balance = applyDelta(product, warehouse, -item.qty());
            inventoryTxnRepository.save(InventoryTxn.dispose(product, warehouse, -item.qty(),
                    req.processedDate(), disposalNo, item.reason()));
            lines.add(new DisposalResponse.Line(product.getId(), product.getCode(), item.qty(), balance));
        }
        return new DisposalResponse(disposalNo, warehouse.getId(), warehouse.getName(), lines,
                stockWarningCollector.drain());
    }

    /**
     * 제품수불부. inventory_txn을 이월/입고/이고/BOM/폐기/출고 버킷으로 집계 → 현재재고(단일 공식).
     * closing(이벤트 합계)과 cachedBalance(inventory.qty)를 대사(reconciled)로 검증.
     */
    @Transactional(readOnly = true)
    public List<StockLedgerRow> stockLedger(LocalDate fromDate, LocalDate toDate,
                                            Long productId, Long warehouseId,
                                            WarehouseType warehouseType) {
        return stockLedger(fromDate, toDate, productId, warehouseId, warehouseType, null);
    }

    /**
     * 제품수불부. {@code keyword}는 도서코드·도서명·창고명을 함께 훑는다(부분일치).
     *
     * <p>★키워드는 <b>메모리에서</b> 거른다. 집계 쿼리가 상품×창고로 묶은 뒤에 걸러야
     * 이월·마감이 정확하다 — SQL WHERE에 넣으면 걸러진 행만으로 이월을 다시 계산하게 된다.
     * 수불부는 화면 한 장 분량(수천 행)이라 메모리에서 걸러도 부담이 없다.
     */
    @Transactional(readOnly = true)
    public List<StockLedgerRow> stockLedger(LocalDate fromDate, LocalDate toDate,
                                            Long productId, Long warehouseId,
                                            WarehouseType warehouseType, String keyword) {
        return stockLedger(fromDate, toDate, productId, warehouseId, warehouseType, keyword, null);
    }

    /** 정렬까지 받는 수불부. {@code sort}는 `필드,방향`(예: `closing,desc`). */
    @Transactional(readOnly = true)
    public List<StockLedgerRow> stockLedger(LocalDate fromDate, LocalDate toDate,
                                            Long productId, Long warehouseId,
                                            WarehouseType warehouseType, String keyword,
                                            String sort) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();

        List<StockLedgerRow> result = new ArrayList<>();
        // 창고구분은 enum 이름 그대로 저장돼 있다(MAIN/CONSIGN). null이면 전체.
        String whType = (warehouseType == null) ? null : warehouseType.name();
        for (Object[] r : inventoryTxnRepository.stockLedger(from, to, productId, warehouseId, whType)) {
            long closing = num(r[15]);
            long cached = num(r[16]);
            long sale = num(r[10]);          // 음수(재고를 깎는다)
            long salesReturn = num(r[13]);   // 양수(되돌아온다)
            // 순매출수량 = 매출 − 반품. 원장은 매출을 음수로 적으므로 부호를 뒤집어 뺀다.
            // ‼️교사용·증정은 무가라 순매출에 안 들어간다 — 순매출조회(SaleRepository)와 같은 정의다.
            long netSaleQty = (-sale) - salesReturn;
            result.add(new StockLedgerRow(
                    num(r[0]), (String) r[1], (String) r[2], num(r[3]), (String) r[4],
                    num(r[5]), num(r[6]), num(r[7]), num(r[8]), num(r[9]),
                    sale, num(r[11]), num(r[12]), salesReturn, num(r[14]),
                    netSaleQty, closing, cached, closing == cached));
        }

        String kw = (keyword == null || keyword.isBlank())
                ? null : keyword.trim().toLowerCase(java.util.Locale.ROOT);
        if (kw != null) {
            result = result.stream()
                    .filter(r -> contains(r.productCode(), kw) || contains(r.productName(), kw)
                            || contains(r.warehouseName(), kw))
                    .toList();
        }
        return sorted(result, sort);
    }

    /**
     * 수불부 정렬. 기본은 도서코드·창고명 순(쿼리 ORDER BY).
     *
     * <p>★<b>메모리에서 정렬한다.</b> 집계가 끝난 행을 다시 세우는 것이라 SQL로 내릴 이유가 없고,
     * 이월·마감이 걸러진 행 기준으로 다시 계산되는 일도 없다(키워드 필터와 같은 이유).
     *
     * <p>‼️모르는 필드는 거부한다. 조용히 기본 순서로 돌려주면 담당자는 정렬이 먹은 줄 알고
     * 위에서부터 몇 개만 보고 판단한다 — 화면엔 오류도 안 뜬다.
     *
     * @param sort {@code 필드,방향} 예: {@code closing,desc}. 방향 생략 시 asc.
     */
    private static List<StockLedgerRow> sorted(List<StockLedgerRow> rows, String sort) {
        if (sort == null || sort.isBlank()) {
            return rows;
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        // ‼️equalsIgnoreCase 를 쓰지 않는다 — 로케일·유니코드 확장에 따라 다른 문자열이
        //   같아질 수 있다(정적분석 IMPROPER_UNICODE). 받아들일 표기를 그대로 나열한다.
        String dir = (parts.length > 1) ? parts[1].trim() : "";
        boolean desc = "desc".equals(dir) || "DESC".equals(dir) || "Desc".equals(dir);

        java.util.Comparator<StockLedgerRow> cmp = switch (field) {
            case "productCode" -> java.util.Comparator.comparing(StockLedgerRow::productCode,
                    java.util.Comparator.nullsLast(String::compareTo));
            case "productName" -> java.util.Comparator.comparing(StockLedgerRow::productName,
                    java.util.Comparator.nullsLast(String::compareTo));
            case "warehouseName" -> java.util.Comparator.comparing(StockLedgerRow::warehouseName,
                    java.util.Comparator.nullsLast(String::compareTo));
            case "opening" -> java.util.Comparator.comparingLong(StockLedgerRow::opening);
            case "inbound" -> java.util.Comparator.comparingLong(StockLedgerRow::inbound);
            case "sale" -> java.util.Comparator.comparingLong(StockLedgerRow::sale);
            case "netSaleQty" -> java.util.Comparator.comparingLong(StockLedgerRow::netSaleQty);
            case "salesReturn" -> java.util.Comparator.comparingLong(StockLedgerRow::salesReturn);
            case "dispose" -> java.util.Comparator.comparingLong(StockLedgerRow::dispose);
            case "closing" -> java.util.Comparator.comparingLong(StockLedgerRow::closing);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "정렬할 수 없는 필드입니다: " + field
                    + " (productCode/productName/warehouseName/opening/inbound/sale/"
                    + "netSaleQty/salesReturn/dispose/closing)");
        };
        return rows.stream().sorted(desc ? cmp.reversed() : cmp).toList();
    }

    /** 키워드 부분일치(대소문자 무시). null 필드는 안 맞는 것으로 본다. */
    private static boolean contains(String v, String kw) {
        return v != null && v.toLowerCase(java.util.Locale.ROOT).contains(kw);
    }

    /** 입고/대체 내역이 다루는 재고이벤트 — 입고·이고·세트조립·세트해체. */
    private static final java.util.List<TxnType> RECORD_KINDS = java.util.List.of(
            TxnType.INBOUND, TxnType.TRANSFER, TxnType.BOM_ASSEMBLE, TxnType.BOM_DISASSEMBLE);

    /**
     * 입고/대체 내역 조회(8p·9p). 등록만 되고 조회가 없던 것을 채운다.
     *
     * <p>★수량 부호를 <b>그대로</b> 준다. 폐기는 "12권 버렸다"라 양수로 뒤집었지만,
     * 여기서는 방향이 곧 정보다 — 이고는 출발(−)·도착(+) 두 줄이고
     * 세트작업도 완제품(+)·구성품(−)으로 갈린다. 절댓값으로 바꾸면 그 구분이 사라진다.
     *
     * <p>도서·창고는 <b>다중선택</b>이다(좌측 트리뷰 체크박스, 2026-08-31 공통 요구).
     *
     * @param kinds 작업구분 필터(TxnType). 비면 네 종류 전부
     */
    @Transactional(readOnly = true)
    public List<StockRecordRow> stockRecords(LocalDate fromDate, LocalDate toDate,
                                             List<Long> productIds, List<Long> warehouseIds,
                                             List<TxnType> kinds) {
        List<TxnType> effectiveKinds = MultiSelect.isAny(kinds) ? RECORD_KINDS : kinds;
        return inventoryTxnRepository
                .findStockRecords(effectiveKinds, fromDate, toDate,
                        MultiSelect.isAny(productIds), MultiSelect.orPlaceholder(productIds, 0L),
                        MultiSelect.isAny(warehouseIds), MultiSelect.orPlaceholder(warehouseIds, 0L)).stream()
                .map(t -> new StockRecordRow(
                        t.getId(), t.getTradeDate(), StockRecordRow.kindOf(t.getTxnType()),
                        t.getWarehouse().getId(), t.getWarehouse().getName(),
                        t.getProduct().getId(), t.getProduct().getCode(), t.getProduct().getName(),
                        t.getProduct().getCatCode(), t.getProduct().getCatName(),
                        t.getQty(), t.getInboundType(), t.getUnitCost(), t.getRefNo(), t.getMemo()))
                .toList();
    }

    /**
     * 폐기 내역 조회(10p). 재고이벤트(DISPOSE)가 원천이다 — 별도 폐기 원장을 두지 않는다.
     *
     * <p>★수량은 원장에 <b>음수</b>로 들어 있다(재고를 깎으므로). 화면에는 양수로 보여야 하니
     * 여기서 부호를 뒤집는다. "10권 버렸다"를 −10으로 보여주면 담당자가 다시 읽어야 한다.
     *
     * <p>★<b>사유와 비고는 같은 칸이다.</b> 등록할 때 사유를 memo에 넣고 있어
     * 지금은 사유만 채우고 비고는 비운다 — 둘을 나누려면 컬럼을 하나 더 만들어야 하고,
     * 그건 화면에서 실제로 둘 다 쓰는지 확인한 뒤에 할 일이다.
     */
    @Transactional(readOnly = true)
    public List<DisposalRecordRow> disposals(LocalDate fromDate, LocalDate toDate,
                                             List<Long> productIds, List<Long> warehouseIds) {
        return inventoryTxnRepository.findDisposals(fromDate, toDate,
                        MultiSelect.isAny(productIds), MultiSelect.orPlaceholder(productIds, 0L),
                        MultiSelect.isAny(warehouseIds), MultiSelect.orPlaceholder(warehouseIds, 0L)).stream()
                .map(t -> new DisposalRecordRow(
                        t.getId(), t.getRefNo(), t.getTradeDate(),
                        t.getWarehouse().getId(), t.getWarehouse().getName(),
                        t.getProduct().getId(), t.getProduct().getCode(), t.getProduct().getName(),
                        t.getProduct().getCatCode(), t.getProduct().getCatName(),
                        Math.abs(t.getQty()), t.getMemo(), null))
                .toList();
    }

    /**
     * 폐기 분류명별 요약(10p). 상세({@link #disposals})가 낱건이라면 이건 분류 단위 합계다.
     * 수량은 양수로 뒤집어 낸다 — 원장은 음수지만 "몇 부 버렸나"에 음수를 주면 다시 읽어야 한다.
     */
    @Transactional(readOnly = true)
    public DisposalSummaryResponse disposalSummary(LocalDate fromDate, LocalDate toDate,
                                                   List<Long> productIds, List<Long> warehouseIds) {
        List<DisposalSummaryResponse.Row> rows = new ArrayList<>();
        long totalCount = 0;
        long totalQty = 0;
        // ★상세(disposals)와 똑같은 필터를 넘긴다 — 갈리면 요약과 상세가 서로 다른 말을 한다.
        for (Object[] r : inventoryTxnRepository.disposalSummaryByCategory(fromDate, toDate,
                MultiSelect.isAny(productIds), MultiSelect.orPlaceholder(productIds, 0L),
                MultiSelect.isAny(warehouseIds), MultiSelect.orPlaceholder(warehouseIds, 0L))) {
            long cnt = num(r[2]);
            long qty = num(r[3]);
            rows.add(new DisposalSummaryResponse.Row((String) r[0], (String) r[1], cnt, qty));
            totalCount += cnt;
            totalQty += qty;
        }
        return new DisposalSummaryResponse(fromDate, toDate, rows, totalCount, totalQty);
    }

    /**
     * 세트 <b>조립·해체 현황</b>(30p). 근거: 발주처 화면검토(2026-08-31) 화면30 —
     * "화면 27(물류 작업비 계산)의 비용 산출과는 <b>연동되지 않도록 분리</b>해,
     * 세트 조립·해체 작업을 진행한 <b>현황(결과)만</b> 보여주는 조회 화면으로 유지".
     *
     * <p>★<b>이 화면은 비용을 말하지 않는다.</b> 발주처가 요구한 '분리'는 화면을 나누는 게 아니라
     * 여기서 금액을 빼는 것이다. 금액 칸이 하나라도 있으면 화면27과 값이 갈리는 순간
     * 어느 쪽이 맞는지 다투게 된다. 그래서 응답에 작업비 필드가 <b>한 칸도 없다</b>.
     *
     * <p>⚠️<b>이전 구현(회차별 포장유형 물량 집계)을 대체한다.</b> 그건 레거시
     * {@code IC회차별작업현황.vb}를 그대로 옮긴 것이라 레거시로서는 맞았지만,
     * 발주처가 "후자라면 조립·해체 현황으로 구성해 달라"고 했다.
     * 포장유형 집계가 필요하면 {@code GET /sales/round-work-status}가 그대로 남아 있다.
     */
    @Transactional(readOnly = true)
    public BomWorkStatusResponse bomWorkStatus(LocalDate fromDate, LocalDate toDate,
                                               List<Long> warehouseIds, String catCode) {
        boolean anyWarehouse = MultiSelect.isAny(warehouseIds);
        java.util.Collection<Long> whIds = MultiSelect.orPlaceholder(warehouseIds, 0L);
        String cat = (catCode == null || catCode.isBlank()) ? null : catCode;

        List<BomWorkStatusResponse.Row> rows = new ArrayList<>();
        long asmCnt = 0;
        long asmQty = 0;
        long disCnt = 0;
        long disQty = 0;
        for (Object[] r : inventoryTxnRepository.bomWorkByProduct(fromDate, toDate, anyWarehouse, whIds, cat)) {
            long ac = num(r[5]);
            long aq = num(r[6]);
            long dc = num(r[7]);
            long dq = num(r[8]);
            rows.add(new BomWorkStatusResponse.Row(
                    (String) r[0], (String) r[1], num(r[2]), (String) r[3], (String) r[4],
                    ac, aq, dc, dq, aq - dq, toLocalDate(r[9])));
            asmCnt += ac;
            asmQty += aq;
            disCnt += dc;
            disQty += dq;
        }

        List<BomWorkStatusResponse.Component> comps = new ArrayList<>();
        for (Object[] c : inventoryTxnRepository.bomWorkComponents(fromDate, toDate, anyWarehouse, whIds)) {
            long used = num(c[3]);
            long back = num(c[4]);
            comps.add(new BomWorkStatusResponse.Component(
                    num(c[0]), (String) c[1], (String) c[2], used, back, used - back));
        }
        return new BomWorkStatusResponse(rows, comps, asmCnt, asmQty, disCnt, disQty);
    }

    /** 네이티브 쿼리의 날짜 컬럼 → LocalDate. 드라이버가 java.sql.Date로 줄 수도, 이미 LocalDate로 줄 수도 있다. */
    private static LocalDate toLocalDate(Object v) {
        if (v instanceof LocalDate d) {
            return d;
        }
        return (v instanceof java.sql.Date d) ? d.toLocalDate() : null;
    }

    /**
     * 제품수불부 <b>결산내역</b>(연초~기준일 누적). 근거: 정본 11p 데이터 항목 +
     * 레거시 제품수불부 「결산내역」 체크박스.
     *
     * <p>★기간을 받지 않는다 — <b>기준일 하나만</b> 받고 시작일은 그 해 1월 1일로 못 박는다.
     * 레거시가 체크박스를 켜는 순간 하는 일이 정확히 그것이다
     * ({@code DateTimePicker_Start.Value = 기준일.ToString("yyyy.01.01")}).
     * 시작일을 호출부가 고를 수 있게 두면 "결산"이라는 이름이 거짓말이 된다.
     *
     * <p>분류 필터도 두지 않는다. 레거시는 결산내역을 켜면 분류 콤보를 <b>비활성</b>시킨다
     * ({@code ComboBox_CatList.Enabled = False}) — 결산은 전 분류를 놓고 소계·총계를 보는 화면이다.
     */
    @Transactional(readOnly = true)
    public List<StockSettlementRow> stockSettlement(LocalDate baseDate, Long productId,
                                                    WarehouseType warehouseType) {
        LocalDate to = (baseDate != null) ? baseDate : LocalDate.now();
        LocalDate from = to.withDayOfYear(1);
        String whType = (warehouseType == null) ? null : warehouseType.name();

        List<StockSettlementRow> rows = new ArrayList<>();
        long[] cat = new long[BUCKETS];      // 분류 소계
        long[] grand = new long[BUCKETS];    // 총계
        String curCat = null;
        boolean catOpen = false;

        for (Object[] r : inventoryTxnRepository.stockSettlement(from, to, productId, whType)) {
            String catCode = (String) r[0];
            if (catOpen && !java.util.Objects.equals(catCode, curCat)) {
                rows.add(StockSettlementRow.catSubtotal(curCat, cat));
                catOpen = false;
            }
            if (!catOpen) {
                curCat = catCode;
                cat = new long[BUCKETS];
                catOpen = true;
            }

            long[] b = new long[BUCKETS];
            for (int i = 0; i < BUCKETS; i++) {
                b[i] = num(r[5 + i]);
                cat[i] += b[i];
                grand[i] += b[i];
            }
            rows.add(StockSettlementRow.detail(catCode, (String) r[1], num(r[2]),
                    (String) r[3], (String) r[4], b));
        }
        if (catOpen) {
            rows.add(StockSettlementRow.catSubtotal(curCat, cat));
        }
        // ‼️행이 하나도 없어도 총계는 낸다 — 빈 화면과 "0으로 결산됐다"는 다른 말이다.
        rows.add(StockSettlementRow.total(grand));
        return rows;
    }

    /**
     * 세트의 <b>회차별</b> 수불 현황(요약 → 회차 → 자재 중 가운데 단계).
     *
     * <p>★<b>여기서 다시 계산하지 않는다.</b> 회차 상품으로 좁혀 {@link #stockLedger}를 부른다 —
     * 화면마다 계산식이 갈리면 세트 합과 회차 합이 어긋난다(3,029/3,006 사고와 같은 이유).
     *
     * <p>‼️<b>거래가 없는 회차도 0으로 담는다.</b> 빼 버리면 담당자는 "이 회차는 왜 없지"를
     * 확인하러 다른 화면을 열어야 한다. BOM에 있으면 구성 회차다.
     */
    @Transactional(readOnly = true)
    public com.daesung.sales.inventory.dto.SetRoundLedgerResponse setRounds(
            Long setProductId, LocalDate fromDate, LocalDate toDate,
            WarehouseType warehouseType) {
        Product set = productRepository.findById(setProductId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "상품이 없습니다. id=" + setProductId));

        List<com.daesung.sales.inventory.dto.SetRoundLedgerResponse.Round> rounds = new ArrayList<>();
        for (BomItem item : bomItemRepository.findByParentId(setProductId)) {
            List<StockLedgerRow> ledger = stockLedger(fromDate, toDate,
                    item.getChild().getId(), null, warehouseType, null);
            rounds.add(new com.daesung.sales.inventory.dto.SetRoundLedgerResponse.Round(
                    item.getRound(), item.getRatio(), ledger));
        }
        rounds.sort(java.util.Comparator.comparingInt(
                com.daesung.sales.inventory.dto.SetRoundLedgerResponse.Round::round));

        return new com.daesung.sales.inventory.dto.SetRoundLedgerResponse(
                set.getId(), set.getCode(), set.getName(), rounds);
    }

    /** 결산 버킷 수(이월·입고·이고·조립해체·폐기·매출·무상·교사용·반품·조정·재고). */
    private static final int BUCKETS = 11;

    private static long num(Object o) {
        return (o == null) ? 0L : ((Number) o).longValue();
    }

    /**
     * 재고 잔량 증감(원자적). delta>=0이면 가산(없으면 생성), delta<0이면 음수재고 방지 차감.
     * 반환값 = 갱신 후 잔량.
     */
    private int applyDelta(Product product, Warehouse warehouse, int delta) {
        // ★음수 재고는 **막지 않는다**. 발주처 확정(2026-08-31 화면7) 원문 —
        //   "재고 음수 차단 로직은 적용되면 안됩니다. 입고 전 출고되는 상품은 재고 (−)로 처리되며,
        //    DSRE에서 수불관리하는 상품도 출고 수량만 나타나 재고가 마이너스로 표시되는 게 정상입니다."
        //
        // ‼️이 판단은 두 번 뒤집혔다. 2026-09-11 사내 점검이 "폐기 초과가 통과된다"를 결함으로 보고
        //   차단을 넣었는데, 발주처 기준으로는 **통과가 정상**이라 다시 걷어냈다.
        //   되돌리기 전에 위 원문을 반드시 확인할 것 — 막으면 "입고 전 출고"가 통째로 불가능해진다.
        //
        //   예외적으로 막아야 할 상황이 생기면 daesung.inventory.block-negative-stock=true.
        if (delta < 0 && inventoryProperties.blockNegative()) {
            // ‼️"조회해서 비교한 뒤 빼는" 방식은 쓰지 않는다. 두 요청이 같은 잔량을 읽고
            //   둘 다 통과해 음수가 된다(read-modify-write). 조건을 UPDATE 문 안에 넣어
            //   **DB가 행을 잠근 채 판단**하게 한다.
            int ok = inventoryRepository.addQtyIfEnough(product.getId(), warehouse.getId(), delta);
            if (ok == 0) {
                // 0은 두 가지다 — 재고 부족, 또는 아직 입고된 적 없어 행 자체가 없음.
                int current = inventoryRepository
                        .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                        .map(Inventory::getQty)
                        .orElse(0);
                throw new BusinessException(ErrorCode.NEGATIVE_STOCK,
                        "재고가 부족합니다. 현재고 " + current + ", 요청 " + (-delta));
            }
            return inventoryRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                    .map(Inventory::getQty)
                    .orElse(0);
        }

        int updated = inventoryRepository.addQty(product.getId(), warehouse.getId(), delta);
        if (updated == 0) {
            // 행이 없으면 만든다. 첫 거래가 출고면 음수로 시작한다 — 그게 "입고 전 출고"다.
            inventoryRepository.save(Inventory.create(product, warehouse, delta));
        }
        int balance = inventoryRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .map(Inventory::getQty)
                .orElse(delta);

        // ‼️막지는 않되 조용히 넘기지도 않는다.
        //   "입고 전 출고"라 정상인 음수와 오타로 생긴 음수는 데이터만 봐서는 구분되지 않는다.
        //   담당자가 그 자리에서 알아채도록 응답에 싣고, 나중에 되짚도록 서버 로그에도 남긴다.
        if (balance < 0) {
            stockWarningCollector.add(com.daesung.sales.inventory.dto.StockWarning.negative(
                    product.getId(), product.getCode(),
                    warehouse.getId(), warehouse.getName(), delta, balance));
            // ★로그에는 **숫자 id만** 넣는다. 상품코드·창고명은 마스터에서 온 문자열이라
            //   줄바꿈이 섞이면 로그 한 줄을 위조할 수 있다(정적분석 CRLF_INJECTION_LOGS).
            log.warn("재고 음수(차단 해제 상태): productId={} warehouseId={} 잔량={} 증감={}",
                    product.getId(), warehouse.getId(), balance, delta);
        }
        return balance;
    }
}
