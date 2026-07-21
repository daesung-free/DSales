package com.daesung.sales.inventory.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.inventory.dto.BomWorkRequest;
import com.daesung.sales.inventory.dto.BomWorkResponse;
import com.daesung.sales.inventory.dto.DisposalRequest;
import com.daesung.sales.inventory.dto.DisposalResponse;
import com.daesung.sales.inventory.dto.InboundRequest;
import com.daesung.sales.inventory.dto.InboundResponse;
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
                    item.unitCost(), req.processedDate(), supplier, item.memo()));
            int currentQty = applyDelta(product, warehouse, item.qty());

            lines.add(new InboundResponse.Line(
                    product.getId(), product.getCode(), item.qty(), currentQty));
        }
        return new InboundResponse(warehouse.getId(), warehouse.getName(), lines);
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

            int fromBal = applyDelta(product, from, -item.qty());
            InventoryTxn outLeg = inventoryTxnRepository.save(
                    InventoryTxn.transfer(product, from, -item.qty(), req.processedDate(), null, item.reason()));
            int toBal = applyDelta(product, to, item.qty());
            inventoryTxnRepository.save(
                    InventoryTxn.transfer(product, to, item.qty(), req.processedDate(), outLeg, item.reason()));

            lines.add(new TransferResponse.Line(
                    product.getId(), product.getCode(), item.qty(), fromBal, toBal));
        }
        return new TransferResponse(from.getId(), from.getName(), to.getId(), to.getName(), lines);
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
                + inventoryTxnRepository.nextPurgeSeq();

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
