package com.daesung.sales.inventory.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.inventory.dto.BomWorkRequest;
import com.daesung.sales.inventory.dto.BomWorkResponse;
import com.daesung.sales.inventory.dto.DisposalRequest;
import com.daesung.sales.inventory.dto.DisposalResponse;
import com.daesung.sales.inventory.dto.InboundRequest;
import com.daesung.sales.inventory.dto.InboundResponse;
import com.daesung.sales.inventory.entity.InboundType;
import com.daesung.sales.inventory.dto.StockLedgerRow;
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

            inventoryTxnRepository.save(InventoryTxn.inbound(product, warehouse, item.qty(),
                    item.unitCost(), req.inboundType(), req.processedDate(), supplier, item.memo()));
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

        // 완제품: 조립 +, 해체 −
        int parentDelta = assemble ? req.workQty() : -req.workQty();
        int parentBal = applyDelta(parent, warehouse, parentDelta);
        inventoryTxnRepository.save(InventoryTxn.bom(parent, warehouse, parentDelta, txnType, req.processedDate(), req.memo()));
        BomWorkResponse.Line parentLine = new BomWorkResponse.Line(
                parent.getId(), parent.getCode(), parentDelta, parentBal);

        // 구성품: 조립 −(비율×수량), 해체 +(비율×수량)
        List<BomWorkResponse.Line> compLines = new ArrayList<>();
        for (BomItem b : boms) {
            Product child = b.getChild();
            int compDelta = (assemble ? -1 : 1) * b.getRatio() * req.workQty();
            int compBal = applyDelta(child, warehouse, compDelta);
            inventoryTxnRepository.save(InventoryTxn.bom(child, warehouse, compDelta, txnType, req.processedDate(), req.memo()));
            compLines.add(new BomWorkResponse.Line(child.getId(), child.getCode(), compDelta, compBal));
        }

        return new BomWorkResponse(warehouse.getId(), warehouse.getName(), req.direction(), parentLine, compLines);
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
                                            Long productId, Long warehouseId) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();

        List<StockLedgerRow> result = new ArrayList<>();
        for (Object[] r : inventoryTxnRepository.stockLedger(from, to, productId, warehouseId)) {
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
