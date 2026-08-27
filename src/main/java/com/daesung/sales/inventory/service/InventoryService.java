package com.daesung.sales.inventory.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.inventory.dto.BomWorkRequest;
import com.daesung.sales.inventory.dto.BomWorkResponse;
import com.daesung.sales.inventory.dto.DisposalRequest;
import com.daesung.sales.inventory.dto.DisposalRecordRow;
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

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final InventoryRepository inventoryRepository;
    private final SequenceService sequenceService;
    private final InventoryTxnRepository inventoryTxnRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final PartnerRepository partnerRepository;
    private final BomItemRepository bomItemRepository;

    /** 일반 입고. 품목마다 (1) 재고이벤트 INBOUND 기록 + (2) 재고 잔량 가산을 한 트랜잭션으로. */
    @Transactional
    public InboundResponse inbound(InboundRequest req) {
        Warehouse warehouse = warehouseRepository.findById(req.destinationWarehouseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "창고가 없습니다. id=" + req.destinationWarehouseId()));
        Partner supplier = partnerRepository.findById(req.supplierClientId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + req.supplierClientId()));

        List<InboundResponse.Line> lines = new ArrayList<>();
        for (InboundRequest.InboundItem item : req.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "상품이 없습니다. id=" + item.productId()));

            InventoryTxn txn = InventoryTxn.inbound(product, warehouse, item.qty(),
                    item.unitCost(), req.inboundType(), req.processedDate(), supplier, item.memo());
            txn.applyLogisCostTarget(Boolean.TRUE.equals(req.logisCostTarget()));   // 물류작업비 대상(8p)
            inventoryTxnRepository.save(txn);
            int currentQty = applyDelta(product, warehouse, item.qty());

            lines.add(new InboundResponse.Line(
                    product.getId(), product.getCode(), item.qty(), currentQty));
        }
        InboundType type = (req.inboundType() != null) ? req.inboundType() : InboundType.NORMAL;
        return new InboundResponse(warehouse.getId(), warehouse.getName(), type, lines);
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
        return new TransferResponse(from.getId(), from.getName(), to.getId(), to.getName(), lines);
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
                null, null, null, parentDelta, parentBal);

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
                    b.getPackType(), compDelta, compBal));
        }

        return new BomWorkResponse(warehouse.getId(), warehouse.getName(), req.direction(),
                parentLine, req.workQty(), compLines);
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
        return new DisposalResponse(disposalNo, warehouse.getId(), warehouse.getName(), lines);
    }

    /**
     * 제품수불부. inventory_txn을 이월/입고/이고/BOM/폐기/출고 버킷으로 집계 → 현재재고(단일 공식).
     * closing(이벤트 합계)과 cachedBalance(inventory.qty)를 대사(reconciled)로 검증.
     */
    @Transactional(readOnly = true)
    public List<StockLedgerRow> stockLedger(LocalDate fromDate, LocalDate toDate,
                                            Long productId, Long warehouseId,
                                            WarehouseType warehouseType) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();

        List<StockLedgerRow> result = new ArrayList<>();
        // 창고구분은 enum 이름 그대로 저장돼 있다(MAIN/CONSIGN). null이면 전체.
        String whType = (warehouseType == null) ? null : warehouseType.name();
        for (Object[] r : inventoryTxnRepository.stockLedger(from, to, productId, warehouseId, whType)) {
            long closing = num(r[15]);
            long cached = num(r[16]);
            result.add(new StockLedgerRow(
                    num(r[0]), (String) r[1], (String) r[2], num(r[3]), (String) r[4],
                    num(r[5]), num(r[6]), num(r[7]), num(r[8]), num(r[9]),
                    num(r[10]), num(r[11]), num(r[12]), num(r[13]), num(r[14]),
                    closing, cached, closing == cached));
        }
        return result;
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
     * @param kind 작업구분 필터(TxnType). null이면 네 종류 전부
     */
    @Transactional(readOnly = true)
    public List<StockRecordRow> stockRecords(LocalDate fromDate, LocalDate toDate,
                                             Long productId, Long warehouseId, TxnType kind) {
        java.util.List<TxnType> kinds = (kind == null) ? RECORD_KINDS : java.util.List.of(kind);
        return inventoryTxnRepository
                .findStockRecords(kinds, fromDate, toDate, productId, warehouseId).stream()
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
                                             Long productId, Long warehouseId) {
        return inventoryTxnRepository.findDisposals(fromDate, toDate, productId, warehouseId).stream()
                .map(t -> new DisposalRecordRow(
                        t.getId(), t.getRefNo(), t.getTradeDate(),
                        t.getWarehouse().getId(), t.getWarehouse().getName(),
                        t.getProduct().getId(), t.getProduct().getCode(), t.getProduct().getName(),
                        t.getProduct().getCatCode(), t.getProduct().getCatName(),
                        Math.abs(t.getQty()), t.getMemo(), null))
                .toList();
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
        if (delta >= 0) {
            int inc = inventoryRepository.addQty(product.getId(), warehouse.getId(), delta);
            if (inc == 0) {
                inventoryRepository.save(Inventory.create(product, warehouse, delta));
            }
        } else {
            int dec = inventoryRepository.addQtyIfEnough(product.getId(), warehouse.getId(), delta);
            if (dec == 0) {
                throw new BusinessException(ErrorCode.NEGATIVE_STOCK,
                        "재고 부족: 상품[" + product.getCode() + "] 창고[" + warehouse.getName() + "]");
            }
        }
        return inventoryRepository.findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .map(Inventory::getQty)
                .orElse(Math.max(delta, 0));
    }
}
