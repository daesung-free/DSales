package com.daesung.sales.sale.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.money.Amounts;
import com.daesung.sales.closing.service.PeriodLockService;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.inventory.entity.TxnType;
import com.daesung.sales.inventory.service.InventoryService;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductPartnerPriceRepository;
import com.daesung.sales.product.repository.ProductRepository;
import com.daesung.sales.sale.dto.ReturnInboundRequest;
import com.daesung.sales.sale.dto.ReturnableAgg;
import com.daesung.sales.sale.dto.ReturnableResponse;
import com.daesung.sales.sale.dto.SaleResponse;
import com.daesung.sales.sale.dto.SalesEntryRequest;
import com.daesung.sales.sale.dto.SalesEntryResponse;
import com.daesung.sales.sale.entity.Sale;
import com.daesung.sales.sale.entity.SalesType;
import com.daesung.sales.sale.repository.SaleRepository;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import com.daesung.sales.salestype.service.OutTypeLookupService;
import com.daesung.sales.warehouse.entity.Warehouse;
import com.daesung.sales.warehouse.repository.WarehouseRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaleService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final SaleRepository saleRepository;
    private final PartnerRepository partnerRepository;
    private final ProductRepository productRepository;
    private final ProductPartnerPriceRepository partnerPriceRepository;
    private final OutTypeLookupService outTypeLookupService;
    private final WarehouseRepository warehouseRepository;
    private final InventoryService inventoryService;
    private final PeriodLockService periodLockService;
    private final SequenceService sequenceService;

    /**
     * 수기 매출 등록(일반 매출) + 재고 반영을 한 트랜잭션으로. 품목마다 금액 산출 → 매출번호(I) 채번 →
     * 매출 원장 기록 + 물류창고 재고 반영(정상출고=−차감/음수재고 방지, 반품=+복구). 위탁출고는 이 API 불가.
     * 반품(RETURN) 라인은 반품입고(29p)와 동일한 교재식 범위검증을 거친다(진입점 무관 동일 규칙).
     */
    @Transactional
    public SalesEntryResponse createEntries(SalesEntryRequest req) {
        periodLockService.assertNotLocked(req.salesDate());
        Partner partner = partnerRepository.findById(req.partnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + req.partnerId()));
        Warehouse warehouse = warehouseRepository.findById(req.warehouseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "물류창고가 없습니다. id=" + req.warehouseId()));

        // 1단계: 라인 해석(상품·회계구분·정가·공급률 자동적용). 저장 전에 전 라인을 확정해야
        //        반품 범위검증을 요청 단위로(순서 무관) 판정할 수 있다.
        List<ResolvedItem> resolved = new ArrayList<>(req.items().size());
        for (SalesEntryRequest.Item item : req.items()) {
            resolved.add(resolve(item, partner));
        }
        assertReturnsWithinRange(partner.getId(), resolved);

        // 2단계: 저장 + 재고 반영.
        String datePart = req.salesDate().format(YYYYMMDD);
        List<SalesEntryResponse.Line> lines = new ArrayList<>();

        for (ResolvedItem r : resolved) {
            SalesEntryRequest.Item item = r.item();
            Product product = r.product();

            Amounts amt = Amounts.of(r.unitPrice(), r.supplyRate(), item.qty(), product.isTaxFree());
            long supplyAmount = amt.supplyAmount();
            long tax = amt.tax();
            long totalAmount = amt.totalAmount();

            String salesNo = "I-" + datePart + "-" + sequenceService.next(SequenceService.SEQ_INVOICE);

            Sale sale = Sale.create(salesNo, req.salesDate(), partner, product,
                    SalesType.NORMAL_SALES, item.shipmentType(), r.salesCategory(),
                    r.unitPrice(), r.supplyRate(), item.qty(),
                    supplyAmount, tax, totalAmount, item.procType(), item.memo());
            sale.applyUploadDetail(item.schoolCode(), item.schoolName(), item.round());   // 학교·회차(12p)
            sale.applyPackType(item.packType());   // 포장구분(회차별 작업현황 집계축)
            saleRepository.save(sale);

            // 재고 반영(한 트랜잭션): 출고유형 → 부호/이벤트유형. 위탁·취소는 이 API 불가.
            // 재고 미관리 상품(모의고사 등 인원기반)은 차감·이벤트 없음. shipmentType 유효성은 항상 검사.
            int delta = stockDelta(item.shipmentType(), item.qty());
            int stockBalance = 0;
            if (product.isStockManaged()) {
                TxnType txnType = (delta >= 0) ? TxnType.RETURN : TxnType.OUTBOUND;
                stockBalance = inventoryService.applyShipment(product, warehouse, delta, txnType,
                        item.shipmentType(), req.salesDate(), salesNo, item.memo());
            }

            lines.add(new SalesEntryResponse.Line(salesNo, product.getId(), product.getCode(),
                    item.shipmentType(), r.salesCategory(), item.qty(), supplyAmount, tax, totalAmount, stockBalance));
        }
        return new SalesEntryResponse(partner.getId(), partner.getName(), lines);
    }

    /** 매출등록 라인 해석 결과(정가·공급률 자동적용까지 확정된 상태). */
    private record ResolvedItem(SalesEntryRequest.Item item, Product product,
                                SalesCategory salesCategory, int unitPrice, int supplyRate) {
    }

    /** 상품 조회 + 회계구분 룩업 + 정가·공급률 자동적용(미입력 시 도서 마스터 정가 / 거래처별 단가 매핑). */
    private ResolvedItem resolve(SalesEntryRequest.Item item, Partner partner) {
        Product product = productRepository.findById(item.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "상품이 없습니다. id=" + item.productId()));
        SalesCategory salesCategory = SalesCategory.valueOf(
                outTypeLookupService.salesCategoryNameOf(item.shipmentType()));

        Integer unitPrice = (item.unitPrice() != null) ? item.unitPrice() : product.getPrice();
        Integer supplyRate = item.supplyRate();
        if (supplyRate == null) {
            supplyRate = partnerPriceRepository
                    .findByProductIdAndPartnerId(product.getId(), partner.getId())
                    .map(m -> m.getSupplyRate()).orElse(null);
        }
        if (unitPrice == null || supplyRate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "정가·공급률이 없고 거래처별 단가 매핑도 없습니다. 상품=" + product.getCode());
        }
        return new ResolvedItem(item, product, salesCategory, unitPrice, supplyRate);
    }

    /**
     * 매출등록 요청 내 반품(RETURN) 라인의 교재식 범위검증. 반품 라인이 없으면 조회조차 하지 않는다.
     * 같은 요청의 판매출고(SALE)는 반품가능수량에 선반영 — 최종 장부 기준으로 판정하므로 라인 순서와 무관.
     */
    private void assertReturnsWithinRange(Long partnerId, List<ResolvedItem> resolved) {
        boolean hasReturn = resolved.stream().anyMatch(r -> r.salesCategory() == SalesCategory.RETURN);
        if (!hasReturn) {
            return;
        }
        Map<String, Long> returnable = loadReturnable(partnerId);
        for (ResolvedItem r : resolved) {
            if (r.salesCategory() == SalesCategory.SALE) {
                returnable.merge(returnableKey(r.product().getId(), r.unitPrice(), r.supplyRate()),
                        (long) r.item().qty(), Long::sum);
            }
        }
        for (ResolvedItem r : resolved) {
            if (r.salesCategory() == SalesCategory.RETURN) {
                consumeReturnable(returnable, r.product(), r.unitPrice(), r.supplyRate(), r.item().qty());
            }
        }
    }

    /**
     * 반품입고(29p 물류 진입점): 한 트랜잭션으로 매출 반품(RETURN) 라인 생성 + 물류창고 재고 복구.
     * 출고유형은 항상 RETURN(담당자가 선택 안 함). 재고관리 상품만 재고 +복구(모의고사 등은 이벤트 없음).
     * 근거: 요구사항 29p — 매출프로그램 단독 사용 시 반품 등록 원천, 8p 자동이고와 동일한 트랜잭션 원자성.
     */
    @Transactional
    public SalesEntryResponse returnInbound(ReturnInboundRequest req) {
        periodLockService.assertNotLocked(req.returnDate());
        Partner partner = partnerRepository.findById(req.partnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + req.partnerId()));
        Warehouse warehouse = warehouseRepository.findById(req.warehouseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "물류창고가 없습니다. id=" + req.warehouseId()));

        String datePart = req.returnDate().format(YYYYMMDD);
        List<SalesEntryResponse.Line> lines = new ArrayList<>();

        // 교재식 반품: 거래처의 도서×정가×공급률별 반품가능수량(누적 출고−기반품) 맵. 한 요청 내 여러 라인은 누적 차감.
        Map<String, Long> returnable = loadReturnable(req.partnerId());

        for (ReturnInboundRequest.Item item : req.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "상품이 없습니다. id=" + item.productId()));

            consumeReturnable(returnable, product, item.unitPrice(), item.supplyRate(), item.qty());

            Amounts amt = Amounts.of(item.unitPrice(), item.supplyRate(), item.qty(), product.isTaxFree());
            long supplyAmount = amt.supplyAmount();
            long tax = amt.tax();
            long totalAmount = amt.totalAmount();

            String salesNo = "I-" + datePart + "-" + sequenceService.next(SequenceService.SEQ_INVOICE);

            // 반품 = shipmentType/회계구분 RETURN 고정. 금액·수량은 양수 저장, 리포트가 반품으로 차감.
            Sale sale = Sale.create(salesNo, req.returnDate(), partner, product,
                    SalesType.NORMAL_SALES, ShipmentType.RETURN, SalesCategory.RETURN,
                    item.unitPrice(), item.supplyRate(), item.qty(),
                    supplyAmount, tax, totalAmount, null, item.memo());
            if (item.sourceOutNo() != null && !item.sourceOutNo().isBlank()) {
                sale.linkSourceOut(item.sourceOutNo());
            }
            saleRepository.save(sale);

            // 물류창고 재고 +복구(한 트랜잭션). 재고 미관리 상품(모의고사 등)은 이벤트 없음.
            int stockBalance = 0;
            if (product.isStockManaged()) {
                stockBalance = inventoryService.applyShipment(product, warehouse, item.qty(), TxnType.RETURN,
                        ShipmentType.RETURN, req.returnDate(), salesNo, item.memo());
            }

            lines.add(new SalesEntryResponse.Line(salesNo, product.getId(), product.getCode(),
                    ShipmentType.RETURN, SalesCategory.RETURN, item.qty(), supplyAmount, tax, totalAmount, stockBalance));
        }
        return new SalesEntryResponse(partner.getId(), partner.getName(), lines);
    }

    /** 반품가능내역 맵 키: 도서×정가×공급률(교재식 반품은 원 출고건 공급률·정가 단위로 범위 관리). */
    private static String returnableKey(Long productId, Integer unitPrice, Integer supplyRate) {
        return productId + ":" + unitPrice + ":" + supplyRate;
    }

    /**
     * 거래처의 도서×정가×공급률별 반품가능수량(누적 판매출고 − 기반품) 맵.
     * 교재식 반품 규칙의 단일 소스 — 반품입고(29p)·매출등록 RETURN 라인이 모두 이걸 쓴다.
     */
    private Map<String, Long> loadReturnable(Long partnerId) {
        Map<String, Long> returnable = new HashMap<>();
        for (ReturnableAgg a : saleRepository.returnableAgg(partnerId, null)) {
            returnable.put(returnableKey(a.getProductId(), a.getUnitPrice(), a.getSupplyRate()),
                    a.getSaleQty() - a.getReturnQty());
        }
        return returnable;
    }

    /**
     * 반품 1건 범위검증 + 잔여 차감(한 요청 내 여러 라인은 누적 차감).
     * 반품수량 ≤ (누적 판매출고 − 기반품), 정가·공급률은 원 출고건과 일치해야 함(불일치=잔여 0 → 거부).
     */
    private void consumeReturnable(Map<String, Long> returnable, Product product,
                                   Integer unitPrice, Integer supplyRate, int qty) {
        String key = returnableKey(product.getId(), unitPrice, supplyRate);
        long remain = returnable.getOrDefault(key, 0L);
        if (qty > remain) {
            throw new BusinessException(ErrorCode.RETURN_EXCEEDS,
                    "반품가능수량 초과: 상품 " + product.getCode() + " 정가 " + unitPrice
                            + " 공급률 " + supplyRate + " → 반품가능 " + remain + ", 요청 " + qty);
        }
        returnable.put(key, remain - qty);
    }

    /**
     * 교재식 반품 가능내역 조회. 거래처(옵션 도서)의 도서×정가×공급률별 반품가능수량(누적 출고−기반품, {@literal >}0만).
     * 프론트는 이 목록에서 라인을 골라 그 범위 내에서 반품 등록(공급률·정가는 원 출고건 고정).
     */
    @Transactional(readOnly = true)
    public ReturnableResponse returnable(Long partnerId, Long productId) {
        List<ReturnableResponse.Row> rows = new ArrayList<>();
        for (ReturnableAgg a : saleRepository.returnableAgg(partnerId, productId)) {
            long remain = a.getSaleQty() - a.getReturnQty();
            if (remain <= 0) {
                continue;
            }
            rows.add(new ReturnableResponse.Row(a.getProductId(), a.getProductCode(), a.getProductName(),
                    a.getUnitPrice(), a.getSupplyRate(), a.getSaleQty(), a.getReturnQty(), remain));
        }
        return new ReturnableResponse(partnerId, rows);
    }

    /** 출고유형별 물류재고 증감 부호. 정상출고/증정/교사용=−차감, 반품=+복구. 위탁·취소는 이 API 불가. */
    private int stockDelta(ShipmentType shipmentType, int qty) {
        return switch (shipmentType) {
            case NORMAL_SHIP, GIFT, TEACHER_USE -> -qty;
            case RETURN -> qty;
            case CONSIGN_SHIP -> throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "위탁출고 매출은 위탁정산(/consignment/settle)으로 처리하세요.");
            case CANCEL -> throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "취소는 매출 취소(/sales/{id}/cancel)로 처리하세요.");
        };
    }

    /**
     * 매출 취소(논리 취소) + 재고 역분개를 한 트랜잭션으로. 이미 취소된 건은 400.
     * 원출고 이벤트(refNo=매출번호)를 반대 부호로 되돌려 재고 복구 + 수불부 버킷 상쇄.
     * 원출고 이벤트가 없으면(위탁정산 매출 등) 재고는 건드리지 않음.
     */
    @Transactional
    public SaleResponse cancel(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "매출이 없습니다. id=" + id));
        if (sale.isCanceled()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 취소된 매출입니다: " + sale.getSalesNo());
        }
        periodLockService.assertNotLocked(sale.getSalesDate());
        sale.cancel();

        // 위탁정산 매출 취소면 미결원장 복원(settled↔remaining 역산). 감사 결함 수정:
        // 이 처리가 없으면 settled_qty가 좌초되어 재정산 불가·재무/물류 desync.
        if (sale.getSalesType() == SalesType.CONSIGN_SALES && sale.getSettlement() != null) {
            sale.getSettlement().getConsignmentOut().unsettle(sale.getSettlement().getSettleQty());
        }

        // 취소 플래그를 먼저 확정(flush)하고 응답을 만든 뒤 역분개.
        // 역분개의 원자적 UPDATE(clearAutomatically)가 세션을 비우므로 순서가 중요.
        saleRepository.flush();
        SaleResponse response = SaleResponse.from(sale);
        inventoryService.reverseShipments(sale.getSalesNo(), LocalDate.now());
        return response;
    }

    /** 통합 매출 조회. */
    @Transactional(readOnly = true)
    public PageResponse<SaleResponse> search(LocalDate from, LocalDate to, SalesCategory salesCategory,
                                             ShipmentType shipmentType, Long partnerId,
                                             boolean includeCanceled, Pageable pageable) {
        return PageResponse.of(
                saleRepository.search(from, to, salesCategory, shipmentType, partnerId, includeCanceled, pageable)
                        .map(SaleResponse::from));
    }

}
