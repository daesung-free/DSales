package com.daesung.sales.sale.service;

import com.daesung.sales.audit.entity.StatusEntityType;
import com.daesung.sales.audit.service.StatusHistoryService;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.money.Amounts;
import com.daesung.sales.common.query.MultiSelect;
import com.daesung.sales.closing.service.PeriodLockService;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.inventory.entity.TxnType;
import com.daesung.sales.inventory.service.InventoryService;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.entity.SalesDivision;
import com.daesung.sales.product.repository.ProductRepository;
import com.daesung.sales.product.repository.SalesDivisionRepository;
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
import com.daesung.sales.salestype.entity.TradeClass;
import com.daesung.sales.salestype.service.OutTypeLookupService;
import com.daesung.sales.warehouse.entity.Warehouse;
import com.daesung.sales.warehouse.repository.WarehouseRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SaleService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final SaleRepository saleRepository;
    private final PartnerRepository partnerRepository;
    private final ProductRepository productRepository;
    private final com.daesung.sales.product.service.PartnerSupplyRateService partnerSupplyRateService;
    private final SalesDivisionRepository salesDivisionRepository;
    private final OutTypeLookupService outTypeLookupService;
    private final WarehouseRepository warehouseRepository;
    private final InventoryService inventoryService;
    private final com.daesung.sales.inventory.service.StockWarningCollector stockWarningCollector;
    private final PeriodLockService periodLockService;
    private final StatusHistoryService statusHistoryService;
    private final SequenceService sequenceService;
    private final com.daesung.sales.logistics.service.ShipmentService shipmentService;

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
        List<SalesEntryResponse.Warning> warnings = collectReturnWarnings(partner.getId(), resolved);

        // 2단계: 저장 + 재고 반영.
        String datePart = req.salesDate().format(YYYYMMDD);
        List<SalesEntryResponse.Line> lines = new ArrayList<>();

        for (ResolvedItem r : resolved) {
            SalesEntryRequest.Item item = r.item();
            Product product = r.product();

            Amounts amt = Amounts.of(r.unitPrice(), r.supplyRate(), item.qty(), product.isTaxFree(),
                    item.tax(), r.discountAmount());
            long supplyAmount = amt.supplyAmount();
            long tax = amt.tax();
            long totalAmount = amt.totalAmount();

            String salesNo = "I-" + datePart + "-" + sequenceService.next(SequenceService.SEQ_INVOICE);

            Sale sale = Sale.create(salesNo, req.salesDate(), partner, product,
                    SalesType.NORMAL_SALES, item.shipmentType(), r.salesCategory(),
                    r.unitPrice(), r.supplyRate(), item.qty(),
                    supplyAmount, tax, totalAmount, item.procType(), item.memo());
            sale.applyDiscount(r.discountAmount());   // 적용된 할인액을 남긴다(매핑은 나중에 바뀐다)
            sale.applyWarehouse(warehouse);   // 출고 창고(7p 재고위치 · 27p 출고창고)
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

            // 물류 작업 단위(발송 건) 확보 — 레거시도 매출등록 시점에 sendData를 만든다(UC_TabPages.vb:752).
            // 반품은 들어오는 물건이라 내보낼 작업이 없다.
            if (r.salesCategory() != SalesCategory.RETURN) {
                shipmentService.ensureFor(sale);
            }

            lines.add(new SalesEntryResponse.Line(salesNo, product.getId(), product.getCode(),
                    item.shipmentType(), r.salesCategory(), item.qty(), supplyAmount, tax, totalAmount, stockBalance));
        }
        // ★재고 경고(음수)를 같은 배열에 합친다 — 화면이 한 곳만 보면 되도록.
        stockWarningCollector.drain().forEach(w -> warnings.add(SalesEntryResponse.Warning.ofStock(w)));
        return new SalesEntryResponse(partner.getId(), partner.getName(), lines, warnings);
    }

    /** 매출등록 라인 해석 결과(정가·공급률 자동적용까지 확정된 상태). */
    private record ResolvedItem(SalesEntryRequest.Item item, Product product,
                                SalesCategory salesCategory, int unitPrice, int supplyRate,
                                Integer discountAmount) {
    }

    /**
     * 상품 조회 + 회계구분 룩업 + 정가·공급률 자동적용.
     *
     * <p>공급률 우선순위: <b>입력값 &gt; 거래처별 매핑(거래처×대분류, 34p) &gt; 도서 기본 공급률</b>.
     * 도서 기본값이 마지막 바탕인 이유는, 발주처가 공급률을 거래처구분별 대표값으로만 운영하기
     * 때문이다(자료요청서 1-2). 바탕값이 없으면 등록을 하려고 전 거래처×전 도서 매핑을 깔아야 한다.
     */
    private ResolvedItem resolve(SalesEntryRequest.Item item, Partner partner) {
        Product product = productRepository.findById(item.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "상품이 없습니다. id=" + item.productId()));
        SalesCategory salesCategory = SalesCategory.valueOf(
                outTypeLookupService.salesCategoryNameOf(item.shipmentType()));

        Integer unitPrice = (item.unitPrice() != null) ? item.unitPrice() : product.getPrice();
        Integer supplyRate = item.supplyRate();
        if (supplyRate == null) {
            Integer mapped = partnerSupplyRateService.rateFor(product, partner.getId());
            supplyRate = (mapped != null) ? mapped : product.getSupplyRate();
        }
        if (unitPrice == null || supplyRate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "정가·공급률이 없고 거래처별 단가 매핑도, 도서 기본 공급률도 없습니다. 상품="
                            + product.getCode());
        }
        // 할인액도 같은 우선순위(입력값 > 거래처×대분류 매핑). 있으면 공급률 대신 금액에 쓰인다.
        Integer discount = (item.discountAmount() != null)
                ? item.discountAmount() : partnerSupplyRateService.discountFor(product, partner.getId());
        return new ResolvedItem(item, product, salesCategory, unitPrice, supplyRate, discount);
    }

    /**
     * 매출등록 요청 내 반품(RETURN) 라인의 교재식 <b>초과 확인</b>. 반품 라인이 없으면 조회조차 하지 않는다.
     * 같은 요청의 판매출고(SALE)는 반품가능수량에 선반영 — 최종 장부 기준으로 보므로 라인 순서와 무관.
     *
     * <p>★막지 않고 <b>경고를 모아 돌려준다</b>(발주처 2026-08-31 화면28). {@code consumeReturnable} 참고.
     */
    private List<SalesEntryResponse.Warning> collectReturnWarnings(Long partnerId, List<ResolvedItem> resolved) {
        List<SalesEntryResponse.Warning> warnings = new ArrayList<>();
        boolean hasReturn = resolved.stream().anyMatch(r -> r.salesCategory() == SalesCategory.RETURN);
        if (!hasReturn) {
            return warnings;
        }
        Map<String, Long> returnable = loadReturnable(partnerId);
        for (ResolvedItem r : resolved) {
            if (r.salesCategory() == SalesCategory.SALE) {
                returnable.merge(returnableKey(r.product().getId()), (long) r.item().qty(), Long::sum);
            }
        }
        for (ResolvedItem r : resolved) {
            if (r.salesCategory() == SalesCategory.RETURN) {
                SalesEntryResponse.Warning w =
                        consumeReturnable(returnable, r.product(), r.item().qty());
                if (w != null) {
                    warnings.add(w);
                }
            }
        }
        return warnings;
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
        List<SalesEntryResponse.Warning> warnings = new ArrayList<>();

        // 교재식 반품: 거래처의 도서×정가×공급률별 반품가능수량(누적 출고−기반품) 맵. 한 요청 내 여러 라인은 누적 차감.
        Map<String, Long> returnable = loadReturnable(req.partnerId());

        for (ReturnInboundRequest.Item item : req.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "상품이 없습니다. id=" + item.productId()));

            SalesEntryResponse.Warning w = consumeReturnable(returnable, product, item.qty());
            if (w != null) {
                warnings.add(w);
            }

            // 반품도 출고와 같은 기준으로 계산되어야 한다 — 할인 매출을 정가 기준으로 되돌리면
            // 반품 금액이 원래 판 금액보다 커진다.
            Integer discount = partnerSupplyRateService.discountFor(product, partner.getId());
            Amounts amt = Amounts.of(item.unitPrice(), item.supplyRate(), item.qty(),
                    product.isTaxFree(), item.tax(), discount);
            long supplyAmount = amt.supplyAmount();
            long tax = amt.tax();
            long totalAmount = amt.totalAmount();

            String salesNo = "I-" + datePart + "-" + sequenceService.next(SequenceService.SEQ_INVOICE);

            // 반품 = shipmentType/회계구분 RETURN 고정. 금액·수량은 양수 저장, 리포트가 반품으로 차감.
            Sale sale = Sale.create(salesNo, req.returnDate(), partner, product,
                    SalesType.NORMAL_SALES, ShipmentType.RETURN, SalesCategory.RETURN,
                    item.unitPrice(), item.supplyRate(), item.qty(),
                    supplyAmount, tax, totalAmount, null, item.memo());
            sale.applyDiscount(discount);
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
        // ★재고 경고(음수)를 같은 배열에 합친다 — 화면이 한 곳만 보면 되도록.
        stockWarningCollector.drain().forEach(w -> warnings.add(SalesEntryResponse.Warning.ofStock(w)));
        return new SalesEntryResponse(partner.getId(), partner.getName(), lines, warnings);
    }

    /**
     * 반품가능내역 맵 키: <b>도서 단위</b>.
     *
     * <p>정가·공급률을 키에 넣지 않는다 — 발주처 확정(2026-08-05 데이터구조 확정사항 2.2):
     * "공급률은 원 출고건 값을 기준으로 <b>표시</b>하되, 담당자가 필요 시 <b>수정 가능</b>해야 함(고정·잠금 아님)".
     * 키에 공급률이 있으면 담당자가 값을 바꾸는 순간 원 출고건을 못 찾아 반품이 거부된다(=사실상 잠금).
     * 범위 판정은 "거래처의 그 도서를 얼마나 내보냈나"만 보고, 정가·공급률은 입력값을 그대로 쓴다.
     */
    private static String returnableKey(Long productId) {
        return String.valueOf(productId);
    }

    /**
     * 거래처의 <b>도서별</b> 반품가능수량(누적 판매출고 − 기반품) 맵.
     * 교재식 반품 규칙의 단일 소스 — 반품입고(29p)·매출등록 RETURN 라인이 모두 이걸 쓴다.
     * 같은 도서가 정가·공급률 달리 여러 번 출고됐어도 도서 단위로 합산한다.
     */
    private Map<String, Long> loadReturnable(Long partnerId) {
        Map<String, Long> returnable = new HashMap<>();
        for (ReturnableAgg a : saleRepository.returnableAgg(partnerId, null)) {
            returnable.merge(returnableKey(a.getProductId()),
                    a.getSaleQty() - a.getReturnQty(), Long::sum);
        }
        return returnable;
    }

    /**
     * 반품 1건 <b>초과 여부 확인</b> + 잔여 차감(한 요청 내 여러 라인은 누적 차감).
     *
     * <p>★<b>막지 않는다.</b> 발주처 화면검토(2026-08-31) 화면28 —
     * "출고내역보다 반품 등록 내역이 더 많이 입력되는 경우 <b>경고 알림(alert)</b>을 넣어주시기 바랍니다."
     * 경고를 요구했지 차단을 요구하지 않았고, 같은 회신에서 재고 음수·초과정산 차단도 함께 걷어냈다(§1).
     * 현장에서는 컷오버 전 출고분이나 다른 경로로 나간 물건이 반품으로 들어온다 —
     * 막으면 실제로 들어온 물건을 장부에 못 적는다.
     *
     * <p>대신 <b>초과분을 숨기지 않는다</b>: 응답에 경고를 담고 서버 로그에도 남긴다.
     * 조용히 통과시키면 담당자는 자기가 초과 입력한 줄 모른다.
     *
     * @return 초과했으면 경고, 아니면 null
     */
    private SalesEntryResponse.Warning consumeReturnable(Map<String, Long> returnable,
                                                         Product product, int qty) {
        String key = returnableKey(product.getId());
        long remain = returnable.getOrDefault(key, 0L);
        returnable.put(key, remain - qty);        // 초과분은 음수로 남는다(다음 라인이 이어서 차감)
        if (qty <= remain) {
            return null;
        }
        long over = qty - Math.max(remain, 0L);
        log.warn("반품 초과(차단 안 함, 발주처 2026-08-31): productId={} 반품가능={} 요청={} 초과={}",
                product.getId(), remain, qty, over);
        return new SalesEntryResponse.Warning("RETURN_EXCEEDS",
                product.getId(), product.getCode(), Math.max(remain, 0L), qty, over,
                "반품 수량이 출고 잔여를 초과했습니다. 도서 " + product.getCode()
                        + " → 반품가능 " + Math.max(remain, 0L) + ", 요청 " + qty + " (초과 " + over + ")");
    }

    /**
     * 교재식 반품 가능내역 조회. 거래처(옵션 도서)의 도서×정가×공급률 단위 출고내역과 반품가능수량.
     *
     * <p>정가·공급률은 <b>원 출고건 값을 화면에 표시</b>해 주기 위한 참고값이다.
     * 담당자가 반품 등록 시 다른 값으로 바꿔도 되고(발주처 확정 2.2), 범위 판정은 도서 단위로만 한다.
     */
    @Transactional(readOnly = true)
    public ReturnableResponse returnable(Long partnerId, Long productId) {
        // 도서 단위로 합산한다 — 범위 판정과 같은 기준이어야 화면의 '반품가능'과 실제 허용치가 어긋나지 않는다.
        // 정가·공급률은 그 도서의 출고 중 수량이 가장 많은 건의 값을 대표로 보여 준다(입력 기본값 용도).
        Map<Long, ReturnableResponse.Row> byProduct = new LinkedHashMap<>();
        Map<Long, Long> repQty = new HashMap<>();
        for (ReturnableAgg a : saleRepository.returnableAgg(partnerId, productId)) {
            ReturnableResponse.Row prev = byProduct.get(a.getProductId());
            long saleQty = a.getSaleQty() + (prev == null ? 0 : prev.saleQty());
            long returned = a.getReturnQty() + (prev == null ? 0 : prev.returnedQty());
            // 대표 정가·공급률: 출고수량이 가장 큰 건
            boolean takeRep = prev == null || a.getSaleQty() > repQty.getOrDefault(a.getProductId(), 0L);
            Integer unitPrice = takeRep ? a.getUnitPrice() : prev.unitPrice();
            Integer supplyRate = takeRep ? a.getSupplyRate() : prev.supplyRate();
            if (takeRep) {
                repQty.put(a.getProductId(), a.getSaleQty());
            }
            byProduct.put(a.getProductId(), new ReturnableResponse.Row(
                    a.getProductId(), a.getProductCode(), a.getProductName(),
                    unitPrice, supplyRate, saleQty, returned, saleQty - returned));
        }
        // ★0만 걸러낸다(더 반품할 것도, 잘못된 것도 없는 도서). **음수는 보여준다** —
        //   반품 초과를 허용한 뒤로(발주처 2026-08-31 화면28) 잔여가 음수가 될 수 있는데,
        //   >0 으로 거르면 초과된 도서가 화면에서 통째로 사라져 담당자가 고칠 방법이 없어진다.
        //   보여야 고친다. (위탁 미결 remainingQty에서 같은 이유로 이미 한 번 고쳤다.)
        List<ReturnableResponse.Row> rows = byProduct.values().stream()
                .filter(r -> r.returnableQty() != 0)
                .toList();
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
        boolean before = sale.isCanceled();   // ★바꾸기 전에 읽는다
        sale.cancel();
        statusHistoryService.record(StatusEntityType.SALE, sale.getId(), "canceled",
                before, true, "매출 취소(" + sale.getSalesNo() + ")");

        // 위탁정산 매출 취소면 미결원장 복원(settled↔remaining 역산). 감사 결함 수정:
        // 이 처리가 없으면 settled_qty가 좌초되어 재정산 불가·재무/물류 desync.
        if (sale.getSalesType() == SalesType.CONSIGN_SALES && sale.getSettlement() != null) {
            sale.getSettlement().getConsignmentOut().unsettle(sale.getSettlement().getSettleQty());
        }

        // 취소 플래그를 먼저 확정(flush)하고 응답을 만든 뒤 역분개.
        // 역분개의 원자적 UPDATE(clearAutomatically)가 세션을 비우므로 순서가 중요.
        saleRepository.flush();
        SaleResponse response = SaleResponse.from(sale, divisionOf(sale));
        inventoryService.reverseShipments(sale.getSalesNo(), LocalDate.now());
        return response;
    }

    /**
     * 통합 매출 조회(7p 출고/반품조회 · 12p 통합매출조회 공용). <b>세 축 모두 다중선택</b>이다
     * (발주처 화면검토 2026-08-31 화면3 — "무상/유상/반품/입고 중복선택 체크박스").
     *
     * <p><b>거래분류</b>(표준 4축 중 첫째)로도 거를 수 있다. 다중선택이 되면서 규칙이 둘 생겼다 —
     * <ul>
     *   <li><b>입고·폐기는 골라도 무시</b>한다. 매출 원장에 대응이 없는 값이다(재고 원장의 거래).
     *       '무상 + 입고'를 고른 담당자는 무상은 보고 싶은 것이지, 입고가 섞였다고 무상까지
     *       사라지길 바라지 않는다. 단 <b>입고·폐기만</b> 골랐다면 남는 조건이 없으므로 빈 결과다 —
     *       전체를 주면 '폐기'로 걸렀는데 매출이 잔뜩 나오는 꼴이 된다.</li>
     *   <li>거래분류와 구분(상세)을 <b>같이</b> 주면 <b>교집합</b>이다. 둘은 다른 질문에 답하는
     *       별개 축이라 둘 다 만족해야 한다. 교집합이 비면 빈 결과다.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public PageResponse<SaleResponse> search(LocalDate from, LocalDate to,
                                             List<SalesCategory> salesCategories,
                                             List<TradeClass> tradeClasses,
                                             List<ShipmentType> shipmentTypes,
                                             List<Long> partnerIds,
                                             List<Long> warehouseIds,
                                             String keyword,
                                             boolean includeCanceled, Pageable pageable) {
        List<SalesCategory> categories = salesCategories;
        if (!MultiSelect.isAny(tradeClasses)) {
            List<SalesCategory> mapped = new ArrayList<>();
            for (TradeClass tc : tradeClasses) {
                SalesCategory m = tc.toSalesCategory();
                if (m != null && !mapped.contains(m)) {
                    mapped.add(m);       // 입고·폐기(null)는 매출 원장에 없다 — 떨어뜨린다
                }
            }
            if (mapped.isEmpty()) {
                return PageResponse.of(Page.empty(pageable));   // 입고·폐기만 골랐다
            }
            if (!MultiSelect.isAny(categories)) {
                mapped.retainAll(categories);                   // 두 축은 교집합
                if (mapped.isEmpty()) {
                    return PageResponse.of(Page.empty(pageable));
                }
            }
            categories = mapped;
        }
        // 세부구분 마스터는 몇 줄이라 한 번에 읽어 맵으로 쓴다 — 행마다 조회하면 목록 한 장에 수백 번이 된다.
        Map<String, SalesDivision> divisions = new HashMap<>();
        for (SalesDivision d : salesDivisionRepository.findAll()) {
            divisions.put(d.getCode(), d);
        }
        // 키워드는 소문자 + 양쪽 와일드카드. 공백만 들어오면 조건에서 뺀다 —
        // 빈 검색어로 전체가 사라지면 담당자는 데이터가 없는 줄 안다.
        String kw = (keyword == null || keyword.isBlank())
                ? null : "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";

        Page<Sale> page = saleRepository.search(from, to,
                MultiSelect.isAny(categories),
                MultiSelect.orPlaceholder(categories, SalesCategory.SALE),
                MultiSelect.isAny(shipmentTypes),
                MultiSelect.orPlaceholder(shipmentTypes, ShipmentType.NORMAL_SHIP),
                MultiSelect.isAny(partnerIds),
                MultiSelect.orPlaceholder(partnerIds, 0L),
                MultiSelect.isAny(warehouseIds),
                MultiSelect.orPlaceholder(warehouseIds, 0L),
                kw, includeCanceled, pageable);
        return PageResponse.of(
                page.map(s -> SaleResponse.from(s, divisions.get(s.getProduct().getSalesDivision()))));
    }

    /** 단건 응답용 세부구분 조회. 미지정이면 null → 대분류가 '미분류'로 나간다. */
    private SalesDivision divisionOf(Sale sale) {
        String code = sale.getProduct().getSalesDivision();
        return (code == null || code.isBlank())
                ? null : salesDivisionRepository.findByCode(code).orElse(null);
    }

}
