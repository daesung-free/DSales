package com.daesung.sales.consignment.service;

import com.daesung.sales.closing.service.PeriodLockService;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.audit.entity.StatusEntityType;
import com.daesung.sales.audit.service.StatusHistoryService;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.money.Amounts;
import com.daesung.sales.consignment.dto.ConsignPendingResponse;
import com.daesung.sales.consignment.dto.ConsignReturnRequest;
import com.daesung.sales.consignment.dto.ConsignReturnResponse;
import com.daesung.sales.consignment.dto.SettlementStatementResponse;
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
    private final com.daesung.sales.inventory.repository.InventoryTxnRepository inventoryTxnRepository;
    private final SequenceService sequenceService;
    private final ConsignmentOutRepository consignmentOutRepository;
    private final ConsignmentSettlementRepository settlementRepository;
    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final PartnerRepository partnerRepository;
    private final PeriodLockService periodLockService;
    private final StatusHistoryService statusHistoryService;

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
            String outNo = "OUT-" + datePart + "-" + sequenceService.next(SequenceService.SEQ_CONSIGNMENT);
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
        periodLockService.assertNotLocked(req.salesDate());
        String datePart = req.salesDate().format(YYYYMMDD);
        List<ConsignSettleResponse.Line> lines = new ArrayList<>();

        for (ConsignSettleRequest.Settlement s : req.settlements()) {
            // 비관적 락으로 로드 — 동시 정산이 같은 미결 행에서 read-modify-write 경합해도
            // 직렬화되어 lost update·초과정산이 발생하지 않는다(이슈#97).
            ConsignmentOut co = consignmentOutRepository.findByIdForUpdate(s.consignmentOutId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "미결(위탁출고)이 없습니다. id=" + s.consignmentOutId()));

            // 초과정산 방지(엔티티 settle에서도 재검증). 잔여 == 0(CLOSED)이면 정산 불가.
            if (s.settleQty() > co.getRemainingQty()) {
                throw new BusinessException(ErrorCode.OVER_SETTLEMENT,
                        "정산 수량이 미결 잔여를 초과했습니다: 잔여 " + co.getRemainingQty()
                                + ", 요청 " + s.settleQty() + " (미결 " + co.getSourceOutNo() + ")");
            }

            Product product = co.getProduct();
            Amounts amt = Amounts.of(s.unitPrice(), s.supplyRate(), s.settleQty(), product.isTaxFree());
            long supplyAmount = amt.supplyAmount();
            long tax = amt.tax();
            long totalAmount = amt.totalAmount();
            String salesNo = "I-" + datePart + "-" + sequenceService.next(SequenceService.SEQ_INVOICE);

            // 정산 이력 → 매출 라인(정산 링크) → 미결 차감
            ConsignmentSettlement settlement = settlementRepository.save(
                    ConsignmentSettlement.create(co, s.settleQty(), salesNo, LocalDateTime.now()));
            saleRepository.save(Sale.createConsign(
                    salesNo, req.salesDate(), co.getPartner(), product,
                    s.unitPrice(), s.supplyRate(), s.settleQty(),
                    supplyAmount, tax, totalAmount, co.getSourceOutNo(), settlement, s.memo()));
            String beforeStatus = co.getStatus().name();   // ★바꾸기 전에 읽는다
            co.settle(s.settleQty());
            if (!beforeStatus.equals(co.getStatus().name())) {
                statusHistoryService.record(StatusEntityType.CONSIGNMENT_OUT, co.getId(), "status",
                        beforeStatus, co.getStatus().name(),
                        "정산 " + s.settleQty() + "건(" + co.getSourceOutNo() + ")");
            }

            lines.add(new ConsignSettleResponse.Line(
                    salesNo, co.getId(), co.getSourceOutNo(), s.settleQty(),
                    co.getRemainingQty(), co.getStatus(), supplyAmount, tax, totalAmount));
        }
        return new ConsignSettleResponse(lines);
    }

    /**
     * 위탁 반품(미정산분). 항목마다 (1) 역-자동이고(위탁창고→물류창고 재고 복귀) + (2) 미결원장 축소.
     * 위탁창고는 원 자동이고 도착다리(origin_txn의 sourceTxn)로 역추적. 매출 무관. 한 트랜잭션.
     */
    @Transactional
    public ConsignReturnResponse returnConsignment(ConsignReturnRequest req) {
        List<ConsignReturnResponse.Line> lines = new ArrayList<>();
        for (ConsignReturnRequest.Item item : req.items()) {
            // 정산과 동일하게 비관적 락 — 반품·정산이 같은 미결 행에서 동시 축소해도 직렬화(이슈#97).
            ConsignmentOut co = consignmentOutRepository.findByIdForUpdate(item.consignmentOutId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "미결(위탁출고)이 없습니다. id=" + item.consignmentOutId()));

            // 원 자동이고: origin_txn=출발다리(물류창고). 도착다리(위탁창고)는 sourceTxn=origin_txn으로 조회.
            InventoryTxn outLeg = co.getOriginTxn();
            if (outLeg == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "원본 자동이고 정보가 없어 반품할 수 없습니다: " + co.getSourceOutNo());
            }
            Warehouse mainWh = outLeg.getWarehouse();  // 물류창고(출발)
            Warehouse consignWh = inventoryTxnRepository.findBySourceTxnId(outLeg.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                            "위탁창고 도착다리를 찾을 수 없습니다: " + co.getSourceOutNo()))
                    .getWarehouse();

            // ★moveStock의 원자적 UPDATE(clearAutomatically)가 컨텍스트를 비우므로,
            //   필요한 값을 먼저 뽑고 co 변경을 flush한 뒤 이고 실행.
            Product product = co.getProduct();
            Long productId = product.getId();
            String productCode = product.getCode();
            String sourceOutNo = co.getSourceOutNo();
            Long coId = co.getId();

            co.returnUnsold(item.returnQty());
            consignmentOutRepository.saveAndFlush(co);   // 컨텍스트 clear 전에 미결 축소 반영
            int remainingAfter = co.getRemainingQty();
            var statusAfter = co.getStatus();

            inventoryService.moveStock(product, consignWh, mainWh, item.returnQty(),
                    req.processedDate(), "위탁 반품 역-자동이고");   // 여기서 컨텍스트 clear

            lines.add(new ConsignReturnResponse.Line(
                    coId, sourceOutNo, productCode, item.returnQty(), remainingAfter, statusAfter,
                    inventoryService.balanceOf(productId, mainWh.getId()),
                    inventoryService.balanceOf(productId, consignWh.getId())));
        }
        return new ConsignReturnResponse(lines);
    }

    /**
     * 정산내역서: 기간 내 위탁 부분정산 이력 + 연결 매출금액 + 미결원장 현황 + 합계.
     * 위탁 회계기준 '정산 시점 매출'(재무팀 확정) 기준. 기간 미지정 시 올해 1/1~오늘.
     */
    @Transactional(readOnly = true)
    public SettlementStatementResponse settlementStatement(LocalDate fromDate, LocalDate toDate) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();

        List<SettlementStatementResponse.Row> rows = new ArrayList<>();
        long cnt = 0, tQty = 0, tSupply = 0, tTax = 0, tTotal = 0;
        for (Object[] r : settlementRepository.settlementStatement(from, to)) {
            LocalDate settledDate = ((java.sql.Date) r[0]).toLocalDate();
            long settleQty = num(r[5]), supply = num(r[7]), tax = num(r[8]), total = num(r[9]);
            rows.add(new SettlementStatementResponse.Row(
                    settledDate, (String) r[1], (String) r[2], (String) r[3], (String) r[4],
                    settleQty, (String) r[6], supply, tax, total,
                    num(r[10]), num(r[11]), num(r[12]), (String) r[13]));
            cnt++;
            tQty += settleQty;
            tSupply += supply;
            tTax += tax;
            tTotal += total;
        }
        var summary = new SettlementStatementResponse.Summary(cnt, tQty, tSupply, tTax, tTotal);
        return new SettlementStatementResponse(from, to, rows, summary);
    }

    private static long num(Object o) {
        return (o == null) ? 0L : ((Number) o).longValue();
    }
}
