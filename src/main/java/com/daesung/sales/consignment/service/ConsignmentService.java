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
import com.daesung.sales.inventory.entity.TxnType;
import com.daesung.sales.inventory.service.InventoryService;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
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

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ConsignmentService.class);

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final InventoryService inventoryService;
    private final com.daesung.sales.product.service.PartnerSupplyRateService partnerSupplyRateService;
    private final com.daesung.sales.inventory.repository.InventoryTxnRepository inventoryTxnRepository;
    private final SequenceService sequenceService;
    private final ConsignmentOutRepository consignmentOutRepository;
    private final ConsignmentSettlementRepository settlementRepository;
    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final PartnerRepository partnerRepository;
    private final PeriodLockService periodLockService;
    private final SettlementDraftService settlementDraftService;
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
                    co.getOriginalQty(), co.getTotalQty(), co.getReturnedQty(),
                    co.getSettledQty(), co.getRemainingQty(), co.getStatus()));
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
            int settleQty = s.settleQtyOrZero();
            int returnQty = s.returnQtyOrZero();
            if (settleQty == 0 && returnQty == 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "정산수량 또는 반품수량 중 하나는 입력해야 합니다. 미결 id=" + s.consignmentOutId());
            }

            // 비관적 락으로 로드 — 동시 정산이 같은 미결 행에서 read-modify-write 경합해도
            // 직렬화되어 lost update·초과정산이 발생하지 않는다(이슈#97).
            ConsignmentOut co = consignmentOutRepository.findByIdForUpdate(s.consignmentOutId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "미결(위탁출고)이 없습니다. id=" + s.consignmentOutId()));

            // ★초과를 막지 않는다(발주처 2026-08-31: "자동 차단하던 기존 로직은 제거,
            //   초과 시 경고 알림만 표시하고 이후 처리는 담당자가 수기로").
            //   대신 초과 사실을 응답에 실어 화면이 alert를 띄울 수 있게 한다.
            String overWarning = null;
            if (settleQty + returnQty > co.getRemainingQty()) {
                overWarning = "미결 잔여를 초과했습니다: 잔여 " + co.getRemainingQty()
                        + ", 정산 " + settleQty + " + 반품 " + returnQty
                        + " (미결 " + co.getSourceOutNo() + ")";
                // 문구는 응답(overWarning)으로 나간다. 로그에는 숫자만 남긴다(CRLF 위조 방지).
                log.warn("위탁 초과 처리(차단 안 함): consignmentOutId={} 잔여={} 정산={} 반품={}",
                        co.getId(), co.getRemainingQty(), settleQty, returnQty);
            }

            String beforeStatus = co.getStatus().name();   // ★바꾸기 전에 읽는다
            String salesNo = null;
            long supplyAmount = 0;
            long tax = 0;
            long totalAmount = 0;

            // (1) 정산분 → 매출 확정
            if (settleQty > 0) {
                if (s.unitPrice() == null || s.supplyRate() == null) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "정산수량을 입력하면 정가·공급률이 필요합니다. 미결 id=" + s.consignmentOutId());
                }
                Product product = co.getProduct();
                // 위탁정산도 거래처×대분류 할인액을 탄다(정산 시점이 매출 시점 — 위탁 회계기준 확정)
                Integer discount = partnerSupplyRateService.discountFor(product, co.getPartner().getId());
                Amounts amt = Amounts.of(s.unitPrice(), s.supplyRate(), settleQty,
                        product.isTaxFree(), s.tax(), discount);
                supplyAmount = amt.supplyAmount();
                tax = amt.tax();
                totalAmount = amt.totalAmount();
                salesNo = "I-" + datePart + "-" + sequenceService.next(SequenceService.SEQ_INVOICE);

                ConsignmentSettlement settlement = settlementRepository.save(
                        ConsignmentSettlement.create(co, settleQty, salesNo, LocalDateTime.now()));
                Sale consignSale = Sale.createConsign(
                        salesNo, req.salesDate(), co.getPartner(), product,
                        s.unitPrice(), s.supplyRate(), settleQty,
                        supplyAmount, tax, totalAmount, co.getSourceOutNo(), settlement, s.memo());
                consignSale.applyDiscount(discount);
                // 위탁정산 매출은 위탁창고에서 나간다(7p 재고위치).
                consignSale.applyWarehouse(consignWarehouseOf(co));
                saleRepository.save(consignSale);
                co.settle(settleQty);

                // ★정산분은 위탁창고에서도 뺀다.
                //   근거: 정본 31p — "실물재고여부 N인 창고(위탁창고)는 실재고 합계에서 제외하고
                //   **별도 미결수량으로만 집계**". 즉 위탁창고 잔량 = 미결 잔여수량이어야 한다.
                //   정산은 "거래처가 팔았다"는 뜻이라 그 물량은 더 이상 우리 것이 아니다.
                //   빼지 않으면 미결이 0인데 위탁창고에는 그대로 남아 두 숫자가 어긋나고,
                //   위탁창고 잔량이 영구 누적돼 숫자 자체가 의미를 잃는다(반품은 이미 빼고 있어 비대칭이기도 했다).
                if (product.isStockManaged()) {
                    // ★applyShipment의 원자적 UPDATE가 영속성 컨텍스트를 비운다.
                    //   미결 변경(co.settle)을 먼저 flush하고, 이후 co를 다시 읽어야
                    //   뒤따르는 반품 처리에서 detached 프록시를 만지지 않는다.
                    Warehouse consignWh = consignWarehouseOf(co);
                    consignmentOutRepository.saveAndFlush(co);
                    inventoryService.applyShipment(product, consignWh, -settleQty,
                            TxnType.OUTBOUND, ShipmentType.CONSIGN_SHIP,
                            req.salesDate(), salesNo, "위탁 정산분 출고");
                    co = consignmentOutRepository.findById(s.consignmentOutId()).orElseThrow();
                }
            }

            // (2) 반품분 → 미결 잔여를 먼저 채우고(Case2), 남는 것은 확정매출분 반품(Case1).
            //     근거: 발주처 화면검토 확인요청서(2026-08-31) 100/60/50 예시.
            int case2 = 0;
            int case1 = 0;
            if (returnQty > 0) {
                int[] split = splitReturn(co, returnQty);
                case2 = split[0];
                case1 = split[1];
                if (case2 > 0) {
                    returnUnsoldStock(co, case2, req.salesDate());
                    // ★moveStock이 영속성 컨텍스트를 비우므로 이후 co를 다시 읽는다.
                    co = consignmentOutRepository.findById(s.consignmentOutId()).orElseThrow();
                }
                if (case1 > 0) {
                    returnSettledStock(co, case1, req.salesDate(),
                            s.unitPrice(), s.supplyRate(), s.tax(), s.memo());
                    co = consignmentOutRepository.findById(s.consignmentOutId()).orElseThrow();
                }
            }

            // 잔량은 정산·반품 어느 쪽으로 줄었든 항상 현재값을 담는다.
            // (반품이 있을 때만 채우면, 정산만 한 줄은 잔량이 0으로 보여 화면이 오해한다)
            Long productId = co.getProduct().getId();
            int mainBalance = inventoryService.balanceOf(productId,
                    co.getOriginTxn().getWarehouse().getId());
            int consignBalance = inventoryService.balanceOf(productId,
                    consignWarehouseOf(co).getId());

            if (!beforeStatus.equals(co.getStatus().name())) {
                statusHistoryService.record(StatusEntityType.CONSIGNMENT_OUT, co.getId(), "status",
                        beforeStatus, co.getStatus().name(),
                        "정산 " + settleQty + " · 반품 " + returnQty + "(" + co.getSourceOutNo() + ")");
            }

            lines.add(new ConsignSettleResponse.Line(
                    salesNo, co.getId(), co.getSourceOutNo(), settleQty, returnQty,
                    case2, case1, overWarning,
                    co.getRemainingQty(), co.getStatus(), supplyAmount, tax, totalAmount,
                    mainBalance, consignBalance));
        }
        // 확정에 쓴 임시저장은 함께 정리한다 — 남겨 두면 "확정했는데 초안이 그대로"라
        // 담당자가 두 번 확정하려 든다. 없는 번호면 조용히 넘어간다(매출은 이미 섰다).
        settlementDraftService.consume(req.fromDraftId());
        return new ConsignSettleResponse(lines);
    }

    /** 반품 이동 결과(물류창고·위탁창고 잔량). */
    private record MovedBalance(int mainBalance, int consignBalance) {
    }

    /**
     * 이 미결의 위탁창고(자동이고 도착다리)를 찾는다.
     * 원 자동이고: origin_txn=출발다리(물류창고), 도착다리는 sourceTxn=origin_txn으로 역추적.
     */
    private Warehouse consignWarehouseOf(ConsignmentOut co) {
        InventoryTxn outLeg = co.getOriginTxn();
        if (outLeg == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "원본 자동이고 정보가 없습니다: " + co.getSourceOutNo());
        }
        return inventoryTxnRepository.findBySourceTxnId(outLeg.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "위탁창고 도착다리를 찾을 수 없습니다: " + co.getSourceOutNo()))
                .getWarehouse();
    }

    /**
     * 위탁 반품 <b>Case 분해</b>. 근거: 발주처 화면검토 확인요청서(2026-08-31).
     *
     * <p>원문 예시: "100부 위탁출고 중 60부 정산 확정(미결잔여 40부) 상태에서 반품 50부가
     * 등록되면, <b>40부는 Case2</b>(위탁창고→물류창고 재고 복구, 매출 영향 없음)로,
     * <b>초과분 10부는 Case1</b>(기존 정산 확정분에 대한 반품, 정산 수량을 50부로 정정 +
     * 매출 마이너스 반영)으로 처리".
     *
     * <p>★<b>미결 잔여를 먼저 채우고 남는 것만 Case1</b>이다. 순서를 뒤집으면
     * 아직 안 판 물건을 "팔린 것을 무른다"로 처리하게 되어 매출이 없는데 매출 반품이 선다.
     *
     * <p>★<b>나간 것보다 많이 돌려받을 수는 없다.</b> 잔여 + 정산누적을 넘는 반품은
     * 거부한다 — 발주처가 풀라고 한 것은 "미결잔여 초과"이지(그건 Case1으로 흡수된다)
     * "원 출고수량 초과"가 아니다. 그대로 두면 정산누적·총출고가 <b>음수</b>가 되어
     * 원출고 = 정산 + 반품 + 잔여가 깨진다.
     *
     * @return {@code [Case2 수량, Case1 수량]}
     */
    private static int[] splitReturn(ConsignmentOut co, int returnQty) {
        int remaining = Math.max(co.getRemainingQty(), 0);
        int settled = Math.max(co.getSettledQty(), 0);
        if (returnQty > remaining + settled) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "반품 수량이 나간 수량을 초과했습니다: 미결잔여 " + remaining
                            + " + 정산누적 " + settled + " = " + (remaining + settled)
                            + ", 요청 " + returnQty + " (미결 " + co.getSourceOutNo() + ")");
        }
        int case2 = Math.min(returnQty, remaining);
        return new int[] {case2, returnQty - case2};
    }

    /**
     * <b>Case 1 — 확정매출분 반품.</b> 매출 반품(RETURN) 라인 생성 + 미결 누적 되돌림 +
     * 실물 재고 복구(물류창고 +).
     *
     * <p>★기존 정산 건({@code ConsignmentSettlement})은 <b>손대지 않는다</b> —
     * 발주처 원문 "특정 정산 건을 소급 수정하지 않고 별도 반품 건을 등록하는 방식".
     *
     * <p>★재고는 <b>물류창고로</b> 돌아온다. 확정매출분은 이미 위탁창고에서도 빠졌고
     * 실물은 거래처에 있었으므로, 돌아오는 자리는 위탁창고가 아니라 물류창고다
     * (일반 반품입고와 같다).
     */
    private void returnSettledStock(ConsignmentOut co, int qty, LocalDate salesDate,
                                    Integer unitPrice, Integer supplyRate, Integer tax, String memo) {
        Product product = co.getProduct();
        Warehouse mainWh = co.getOriginTxn().getWarehouse();

        int price = (unitPrice != null) ? unitPrice : settledUnitPriceOf(co);
        int rate = (supplyRate != null) ? supplyRate : settledSupplyRateOf(co);
        Integer discount = partnerSupplyRateService.discountFor(product, co.getPartner().getId());
        Amounts amt = Amounts.of(price, rate, qty, product.isTaxFree(), tax, discount);

        String salesNo = "I-" + salesDate.format(YYYYMMDD) + "-"
                + sequenceService.next(SequenceService.SEQ_INVOICE);
        Sale ret = Sale.createBulk(salesNo, salesDate, co.getPartner(), product,
                ShipmentType.RETURN, SalesCategory.RETURN, price, rate, qty,
                amt.supplyAmount(), amt.tax(), amt.totalAmount(),
                (memo != null ? memo + " / " : "") + "위탁 확정매출분 반품(Case1) " + co.getSourceOutNo(),
                null);
        ret.applyWarehouse(mainWh);
        saleRepository.save(ret);

        co.returnSettled(qty);
        consignmentOutRepository.saveAndFlush(co);

        if (product.isStockManaged()) {
            inventoryService.applyShipment(product, mainWh, qty, TxnType.RETURN,
                    ShipmentType.RETURN, salesDate, salesNo, "위탁 확정매출분 반품 입고");
        }
    }

    /** 확정매출분 반품의 정가 — 입력이 없으면 그 미결의 마지막 위탁정산 매출에서 가져온다. */
    private int settledUnitPriceOf(ConsignmentOut co) {
        return saleRepository.findLatestConsignSale(co.getId())
                .map(Sale::getUnitPrice)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "확정매출분 반품에 쓸 정가가 없습니다. 정가·공급률을 입력하세요. 미결="
                                + co.getSourceOutNo()));
    }

    /** 확정매출분 반품의 공급률 — 정가와 같은 출처(마지막 위탁정산 매출). */
    private int settledSupplyRateOf(ConsignmentOut co) {
        return saleRepository.findLatestConsignSale(co.getId())
                .map(Sale::getSupplyRate)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "확정매출분 반품에 쓸 공급률이 없습니다. 정가·공급률을 입력하세요. 미결="
                                + co.getSourceOutNo()));
    }

    /**
     * 미정산분 반품 처리 — 위탁창고→물류창고 역-자동이고 + 미결 축소.
     * 정산(settle)과 단독 반품(returnConsignment) 양쪽에서 쓰는 공통 경로다.
     */
    private MovedBalance returnUnsoldStock(ConsignmentOut co, int returnQty, LocalDate processedDate) {
        Warehouse mainWh = co.getOriginTxn().getWarehouse();   // 물류창고(출발)
        Warehouse consignWh = consignWarehouseOf(co);           // 위탁창고(도착) — 정산과 같은 경로

        // ★moveStock의 원자적 UPDATE(clearAutomatically)가 컨텍스트를 비우므로,
        //   필요한 값을 먼저 뽑고 co 변경을 flush한 뒤 이고 실행.
        Product product = co.getProduct();
        Long productId = product.getId();

        co.returnUnsold(returnQty);
        consignmentOutRepository.saveAndFlush(co);   // 컨텍스트 clear 전에 미결 축소 반영

        inventoryService.moveStock(product, consignWh, mainWh, returnQty,
                processedDate, "위탁 반품 역-자동이고");   // 여기서 컨텍스트 clear

        return new MovedBalance(
                inventoryService.balanceOf(productId, mainWh.getId()),
                inventoryService.balanceOf(productId, consignWh.getId()));
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
            String sourceOutNo = co.getSourceOutNo();
            Long coId = co.getId();
            String productCode = co.getProduct().getCode();

            // ★초과를 막지 않고 Case2/Case1으로 가른다 — 정산 화면(반품수량 칸)과 같은 규칙이다.
            //   두 경로에서 규칙이 갈리면 같은 반품이 어디로 들어왔느냐에 따라 결과가 달라진다.
            int[] split = splitReturn(co, item.returnQty());
            int case2 = split[0];
            int case1 = split[1];
            String overWarning = (case1 > 0)
                    ? "미결 잔여를 초과했습니다: 잔여 " + co.getRemainingQty()
                        + ", 요청 " + item.returnQty() + " → 초과분 " + case1 + "부는 확정매출분 반품(Case1)"
                    : null;
            if (overWarning != null) {
                log.warn("위탁 반품 초과 처리(차단 안 함): consignmentOutId={} 잔여={} 요청={} Case1={}",
                        coId, co.getRemainingQty(), item.returnQty(), case1);
            }

            MovedBalance moved = new MovedBalance(0, 0);
            if (case2 > 0) {
                moved = returnUnsoldStock(co, case2, req.processedDate());
                co = consignmentOutRepository.findById(coId).orElseThrow();
            }
            if (case1 > 0) {
                // 단독 반품에는 정가·공급률 입력칸이 없다 → 마지막 위탁정산 매출에서 가져온다.
                returnSettledStock(co, case1, req.processedDate(), null, null, null, item.memo());
                co = consignmentOutRepository.findById(coId).orElseThrow();
                Long pid = co.getProduct().getId();
                moved = new MovedBalance(
                        inventoryService.balanceOf(pid, co.getOriginTxn().getWarehouse().getId()),
                        inventoryService.balanceOf(pid, consignWarehouseOf(co).getId()));
            }
            ConsignmentOut after = consignmentOutRepository.findById(coId).orElseThrow();

            lines.add(new ConsignReturnResponse.Line(
                    coId, sourceOutNo, productCode, item.returnQty(),
                    case2, case1, overWarning,
                    after.getRemainingQty(), after.getStatus(),
                    moved.mainBalance(), moved.consignBalance()));
        }
        return new ConsignReturnResponse(lines);
    }

    /**
     * 정산내역서: 기간 내 위탁 부분정산 이력 + 연결 매출금액 + 미결원장 현황 + 합계.
     * 위탁 회계기준 '정산 시점 매출'(재무팀 확정) 기준. 기간 미지정 시 올해 1/1~오늘.
     */
    @Transactional(readOnly = true)
    public SettlementStatementResponse settlementStatement(LocalDate fromDate, LocalDate toDate,
                                                          Long partnerId) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();

        List<SettlementStatementResponse.Row> rows = new ArrayList<>();
        long cnt = 0, tQty = 0, tSupply = 0, tTax = 0, tTotal = 0;
        for (Object[] r : settlementRepository.settlementStatement(from, to, partnerId)) {
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
