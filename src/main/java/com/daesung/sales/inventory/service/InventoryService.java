package com.daesung.sales.inventory.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.inventory.dto.InboundRequest;
import com.daesung.sales.inventory.dto.InboundResponse;
import com.daesung.sales.inventory.dto.TransferRequest;
import com.daesung.sales.inventory.dto.TransferResponse;
import com.daesung.sales.inventory.entity.Inventory;
import com.daesung.sales.inventory.entity.InventoryTxn;
import com.daesung.sales.inventory.repository.InventoryRepository;
import com.daesung.sales.inventory.repository.InventoryTxnRepository;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductRepository;
import com.daesung.sales.warehouse.entity.Warehouse;
import com.daesung.sales.warehouse.repository.WarehouseRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTxnRepository inventoryTxnRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final PartnerRepository partnerRepository;

    /**
     * 일반 입고. 품목마다 (1) 재고이벤트 INBOUND 기록 + (2) 재고 잔량 가산을 한 트랜잭션으로 처리.
     */
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

            // (1) 재고 이벤트 기록
            InventoryTxn txn = InventoryTxn.inbound(product, warehouse, item.qty(),
                    item.unitCost(), req.processedDate(), supplier, item.memo());
            inventoryTxnRepository.save(txn);

            // (2) 재고 잔량 갱신 — 원자적 증가(lost update 방지). 행이 없으면 신규 생성.
            int updated = inventoryRepository.addQty(product.getId(), warehouse.getId(), item.qty());
            if (updated == 0) {
                inventoryRepository.save(Inventory.create(product, warehouse, item.qty()));
            }
            int currentQty = inventoryRepository
                    .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                    .map(Inventory::getQty)
                    .orElse(item.qty());

            lines.add(new InboundResponse.Line(
                    product.getId(), product.getCode(), item.qty(), currentQty));
        }
        return new InboundResponse(warehouse.getId(), warehouse.getName(), lines);
    }

    /**
     * 단순 이고(창고 이동). 출발창고 −qty(음수재고 방지), 도착창고 +qty. 매출 미발생.
     * 품목마다 재고이벤트 2다리(출발 −, 도착 +)를 source로 연결. 전체 한 트랜잭션.
     */
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

            // 출발창고 차감(음수재고 방지)
            int dec = inventoryRepository.addQtyIfEnough(product.getId(), from.getId(), -item.qty());
            if (dec == 0) {
                throw new BusinessException(ErrorCode.NEGATIVE_STOCK,
                        "재고 부족: 창고[" + from.getName() + "] 상품[" + product.getCode() + "]");
            }
            InventoryTxn outLeg = inventoryTxnRepository.save(
                    InventoryTxn.transfer(product, from, -item.qty(), req.processedDate(), null, item.reason()));

            // 도착창고 증가(없으면 생성)
            int inc = inventoryRepository.addQty(product.getId(), to.getId(), item.qty());
            if (inc == 0) {
                inventoryRepository.save(Inventory.create(product, to, item.qty()));
            }
            inventoryTxnRepository.save(
                    InventoryTxn.transfer(product, to, item.qty(), req.processedDate(), outLeg, item.reason()));

            int fromBal = inventoryRepository.findByProductIdAndWarehouseId(product.getId(), from.getId())
                    .map(Inventory::getQty).orElse(0);
            int toBal = inventoryRepository.findByProductIdAndWarehouseId(product.getId(), to.getId())
                    .map(Inventory::getQty).orElse(item.qty());
            lines.add(new TransferResponse.Line(
                    product.getId(), product.getCode(), item.qty(), fromBal, toBal));
        }
        return new TransferResponse(from.getId(), from.getName(), to.getId(), to.getName(), lines);
    }
}
