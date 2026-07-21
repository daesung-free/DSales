package com.daesung.sales.consignment.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.consignment.dto.ConsignPendingResponse;
import com.daesung.sales.consignment.dto.ConsignSettleRequest;
import com.daesung.sales.consignment.dto.ConsignSettleResponse;
import com.daesung.sales.consignment.dto.ConsignmentOutRequest;
import com.daesung.sales.consignment.dto.ConsignmentOutResponse;
import com.daesung.sales.consignment.entity.ConsignmentOut;
import com.daesung.sales.consignment.entity.ConsignmentSettlement;
import com.daesung.sales.consignment.repository.ConsignmentOutRepository;
import com.daesung.sales.consignment.repository.ConsignmentSettlementRepository;
import com.daesung.sales.inventory.entity.InventoryTxn;
import com.daesung.sales.inventory.service.InventoryService;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductRepository;
import com.daesung.sales.sale.entity.Sale;
import com.daesung.sales.sale.repository.SaleRepository;
import com.daesung.sales.warehouse.entity.Warehouse;
import com.daesung.sales.warehouse.repository.WarehouseRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위탁 로직(로직 B). 물류 재고와 재무 매출을 분리하는 이 프로젝트의 심장.
 *  - 위탁출고: 물류창고 → 위탁창고 이고 + 미결원장 생성 (재무장부 미반영)
 *  - 미결정산: 부분/전량 정산 → 매출 확정 (재고 미변동), 불변식·초과정산 방지
 */
@Service
@RequiredArgsConstructor
public class ConsignmentService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final InventoryService inventoryService;
    private final ConsignmentOutRepository consignmentOutRepository;
    private final ConsignmentSettlementRepository settlementRepository;
    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final PartnerRepository partnerRepository;

    /** 위탁출고. 품목마다 물류→위탁 이고 + 미결(OPEN) 생성. 한 트랜잭션. 매출 미발생. */
    @Transactional
    public ConsignmentOutResponse consignmentOut(ConsignmentOutRequest req) {
        if (req.fromWarehouseId().equals(req.toWarehouseId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "출발/도착 창고가 같습니다.");
        }
        Partner partner = partnerRepository.findById(req.partnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + req.partnerId()));
        Warehouse from = warehouseRepository.findById(req.fromWarehouseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "출발 창고가 없습니다. id=" + req.fromWarehouseId()));
        Warehouse to = warehouseRepository.findById(req.toWarehouseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "도착 창고가 없습니다. id=" + req.toWarehouseId()));

        String datePart = req.processedDate().format(YYYYMMDD);
        List<ConsignmentOutResponse.Line> lines = new ArrayList<>();
        for (ConsignmentOutRequest.Item item : req.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "상품이 없습니다. id=" + item.productId()));

            // 1) 재고 이동(물류 −, 위탁 +) — 출발다리 이벤트를 미결의 origin_txn으로 링크
            InventoryTxn outLeg = inventoryService.moveStock(
                    product, from, to, item.qty(), req.processedDate(), "위탁출고 자동이고");

            // 2) 미결원장 생성
            String outNo = "OUT-" + datePart + "-" + consignmentOutRepository.nextConsignSeq();
            ConsignmentOut co = consignmentOutRepository.save(
                    ConsignmentOut.create(outNo, product, partner, item.qty(), outLeg));

            lines.add(new ConsignmentOutResponse.Line(
                    co.getId(), outNo, product.getId(), product.getCode(), item.qty(),
                    inventoryService.balanceOf(product.getId(), from.getId()),
                    inventoryService.balanceOf(product.getId(), to.getId())));
        }
        return new ConsignmentOutResponse(
                partner.getId(), partner.getName(), from.getId(), to.getId(), lines);
    }

    /** 거래처별 미결(잔여>0) 조회. */
    @Transactional(readOnly = true)
    public ConsignPendingResponse findPending(Long partnerId) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + partnerId));

        List<ConsignPendingResponse.Line> lines = new ArrayList<>();
        for (ConsignmentOut co : consignmentOutRepository.findPending(partnerId)) {
            Product p = co.getProduct();
            lines.add(new ConsignPendingResponse.Line(
                    co.getId(), co.getSourceOutNo(), p.getId(), p.getCode(), p.getName(),
                    co.getTotalQty(), co.getSettledQty(), co.getRemainingQty(), co.getStatus()));
        }
        return new ConsignPendingResponse(partner.getId(), partner.getName(), lines);
    }

    /**
     * 위탁 미결정산. 항목마다 (1) 매출 확정 라인 생성 + (2) 미결 차감(불변식·초과정산 방지).
     * 재고는 위탁출고 시 이미 반영됨 — 여기선 건드리지 않는다. 한 트랜잭션.
     */
    @Transactional
    public ConsignSettleResponse settle(ConsignSettleRequest req) {
        String datePart = req.salesDate().format(YYYYMMDD);
        List<ConsignSettleResponse.Line> lines = new ArrayList<>();

        for (ConsignSettleRequest.Settlement s : req.settlements()) {
            ConsignmentOut co = consignmentOutRepository.findById(s.consignmentOutId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "미결(위탁출고)이 없습니다. id=" + s.consignmentOutId()));

            // 초과정산 방지(엔티티 settle에서도 재검증). 잔여 == 0(CLOSED)이면 정산 불가.
            if (s.settleQty() > co.getRemainingQty()) {
                throw new BusinessException(ErrorCode.OVER_SETTLEMENT,
                        "정산 수량이 미결 잔여를 초과했습니다: 잔여 " + co.getRemainingQty()
                                + ", 요청 " + s.settleQty() + " (미결 " + co.getSourceOutNo() + ")");
            }

            Product product = co.getProduct();
            long supplyAmount = (long) ((double) s.unitPrice() * s.supplyRate() / 100.0 * s.settleQty());
            long tax = product.isTaxFree() ? 0L : supplyAmount / 10L;
            long totalAmount = supplyAmount + tax;
            String salesNo = "I-" + datePart + "-" + saleRepository.nextInvoiceSeq();

            // 정산 이력 → 매출 라인(정산 링크) → 미결 차감
            ConsignmentSettlement settlement = settlementRepository.save(
                    ConsignmentSettlement.create(co, s.settleQty(), salesNo, LocalDateTime.now()));
            saleRepository.save(Sale.createConsign(
                    salesNo, req.salesDate(), co.getPartner(), product,
                    s.unitPrice(), s.supplyRate(), s.settleQty(),
                    supplyAmount, tax, totalAmount, co.getSourceOutNo(), settlement, s.memo()));
            co.settle(s.settleQty());

            lines.add(new ConsignSettleResponse.Line(
                    salesNo, co.getId(), co.getSourceOutNo(), s.settleQty(),
                    co.getRemainingQty(), co.getStatus(), supplyAmount, tax, totalAmount));
        }
        return new ConsignSettleResponse(lines);
    }
}
