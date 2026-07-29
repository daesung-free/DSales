package com.daesung.sales.sale.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.money.Amounts;
import com.daesung.sales.closing.config.SupplierProperties;
import com.daesung.sales.closing.service.PeriodLockService;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.inventory.entity.TxnType;
import com.daesung.sales.inventory.repository.InventoryTxnRepository;
import com.daesung.sales.inventory.service.InventoryService;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductPartnerPriceRepository;
import com.daesung.sales.product.repository.ProductRepository;
import com.daesung.sales.sale.dto.BookInoutResponse;
import com.daesung.sales.sale.dto.BookSalesAgg;
import com.daesung.sales.sale.dto.CategorySalesAgg;
import com.daesung.sales.sale.dto.MonthlyStatementResponse;
import com.daesung.sales.sale.dto.ReturnInboundRequest;
import com.daesung.sales.sale.dto.CategorySalesResponse;
import com.daesung.sales.sale.dto.SaleResponse;
import com.daesung.sales.sale.dto.SalesEntryRequest;
import com.daesung.sales.sale.dto.SalesEntryResponse;
import com.daesung.sales.sale.dto.NetSalesResponse;
import com.daesung.sales.sale.dto.SalesStatementAgg;
import com.daesung.sales.sale.dto.SalesStatementResponse;
import com.daesung.sales.sale.dto.PartnerProductSalesAgg;
import com.daesung.sales.sale.dto.SalesStatementRow;
import com.daesung.sales.sale.dto.TransactionStatementResponse;
import com.daesung.sales.sale.dto.YoyComparisonResponse;
import com.daesung.sales.sale.dto.SalesSummaryResponse;
import com.daesung.sales.sale.dto.SalesSummaryRow;
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
    private final InventoryTxnRepository inventoryTxnRepository;
    private final PeriodLockService periodLockService;
    private final SupplierProperties supplier;
    private final SequenceService sequenceService;

    /**
     * 수기 매출 등록(일반 매출) + 재고 반영을 한 트랜잭션으로. 품목마다 금액 산출 → 매출번호(I) 채번 →
     * 매출 원장 기록 + 물류창고 재고 반영(정상출고=−차감/음수재고 방지, 반품=+복구). 위탁출고는 이 API 불가.
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

        String datePart = req.salesDate().format(YYYYMMDD);
        List<SalesEntryResponse.Line> lines = new ArrayList<>();

        for (SalesEntryRequest.Item item : req.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "상품이 없습니다. id=" + item.productId()));
            SalesCategory salesCategory = SalesCategory.valueOf(
                    outTypeLookupService.salesCategoryNameOf(item.shipmentType()));

            // 정가·공급률 자동적용: 미입력 시 정가=도서 마스터 정가, 공급률=거래처별 단가 매핑
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

            Amounts amt = Amounts.of(unitPrice, supplyRate, item.qty(), product.isTaxFree());
            long supplyAmount = amt.supplyAmount();
            long tax = amt.tax();
            long totalAmount = amt.totalAmount();

            String salesNo = "I-" + datePart + "-" + sequenceService.next(SequenceService.SEQ_INVOICE);

            Sale sale = Sale.create(salesNo, req.salesDate(), partner, product,
                    SalesType.NORMAL_SALES, item.shipmentType(), salesCategory,
                    unitPrice, supplyRate, item.qty(),
                    supplyAmount, tax, totalAmount, item.procType(), item.memo());
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
                    item.shipmentType(), salesCategory, item.qty(), supplyAmount, tax, totalAmount, stockBalance));
        }
        return new SalesEntryResponse(partner.getId(), partner.getName(), lines);
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

        for (ReturnInboundRequest.Item item : req.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "상품이 없습니다. id=" + item.productId()));

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

    /**
     * 순매출 집계(상품별 + 합계행). 취소 제외. 매출/증정/교사용/반품 버킷 + 순매출(매출−반품).
     * 기간 미지정 시 올해 1/1~오늘.
     */
    @Transactional(readOnly = true)
    public SalesSummaryResponse summary(LocalDate fromDate, LocalDate toDate, Long partnerId) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();

        List<SalesSummaryRow> rows = new ArrayList<>();
        long tSaleQ = 0, tSaleA = 0, tFreeQ = 0, tFreeA = 0, tTchQ = 0, tTchA = 0,
                tRetQ = 0, tRetA = 0, tTax = 0, tTotal = 0;
        for (Object[] r : saleRepository.salesSummary(from, to, partnerId)) {
            long saleQty = num(r[3]), saleAmt = num(r[4]), freeQty = num(r[5]), freeAmt = num(r[6]),
                    tchQty = num(r[7]), tchAmt = num(r[8]), retQty = num(r[9]), retAmt = num(r[10]),
                    tax = num(r[11]), total = num(r[12]);
            rows.add(new SalesSummaryRow(
                    num(r[0]), (String) r[1], (String) r[2],
                    saleQty, saleAmt, freeQty, freeAmt, tchQty, tchAmt, retQty, retAmt,
                    saleQty - retQty, saleAmt - retAmt, tax, total));
            tSaleQ += saleQty; tSaleA += saleAmt; tFreeQ += freeQty; tFreeA += freeAmt;
            tTchQ += tchQty; tTchA += tchAmt; tRetQ += retQty; tRetA += retAmt; tTax += tax; tTotal += total;
        }
        SalesSummaryRow total = new SalesSummaryRow(null, "합계", null,
                tSaleQ, tSaleA, tFreeQ, tFreeA, tTchQ, tTchA, tRetQ, tRetA,
                tSaleQ - tRetQ, tSaleA - tRetA, tTax, tTotal);
        return new SalesSummaryResponse(from, to, rows, total);
    }

    /**
     * 콘텐츠구분 순매출. SELF=매출−반품, EXTERNAL=매출−매입=이익(매입원가=매입입고 unit_cost 가중평균).
     * 기간 미지정 시 올해 1/1~오늘. contentType: null/전체, SELF, EXTERNAL.
     */
    @Transactional(readOnly = true)
    public NetSalesResponse netSales(LocalDate fromDate, LocalDate toDate, String contentType) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();
        String filter = (contentType == null || contentType.isBlank()) ? null : contentType.trim().toUpperCase();

        // 상품별 매입원가(입고 가중평균)
        Map<Long, Long> avgCost = new HashMap<>();
        for (Object[] r : inventoryTxnRepository.avgInboundCostByProduct()) {
            avgCost.put(num(r[0]), num(r[1]));
        }

        List<NetSalesResponse.Row> rows = new ArrayList<>();
        long tSaleQ = 0, tSaleA = 0, tFreeA = 0, tRetQ = 0, tRetA = 0, tPurch = 0;
        boolean anyExternal = false;
        for (Object[] r : saleRepository.netSalesByProduct(from, to, filter)) {
            long pid = num(r[0]);
            String ct = (String) r[3];
            long saleQty = num(r[4]), saleAmt = num(r[5]), freeAmt = num(r[6]), retQty = num(r[7]), retAmt = num(r[8]);
            long netQty = saleQty - retQty;
            long netAmt = saleAmt - retAmt;

            Long unitCost = null, purchase = null, profit = null;
            Double margin = null;
            if ("EXTERNAL".equals(ct)) {
                anyExternal = true;
                unitCost = avgCost.getOrDefault(pid, 0L);
                purchase = unitCost * netQty;
                profit = netAmt - purchase;
                margin = (netAmt != 0) ? Math.round((double) profit / netAmt * 100 * 10) / 10.0 : null;
                tPurch += purchase;
            }
            rows.add(new NetSalesResponse.Row(pid, (String) r[1], (String) r[2], ct,
                    saleQty, saleAmt, freeAmt, retQty, retAmt, netQty, netAmt,
                    unitCost, purchase, profit, margin));
            tSaleQ += saleQty; tSaleA += saleAmt; tFreeA += freeAmt; tRetQ += retQty; tRetA += retAmt;
        }

        long tNetAmt = tSaleA - tRetA;
        Long totalPurchase = anyExternal ? tPurch : null;
        Long totalProfit = anyExternal ? (tNetAmt - tPurch) : null;
        Double totalMargin = (anyExternal && tNetAmt != 0) ? Math.round((double) totalProfit / tNetAmt * 100 * 10) / 10.0 : null;
        NetSalesResponse.Row total = new NetSalesResponse.Row(null, "합계", null, null,
                tSaleQ, tSaleA, tFreeA, tRetQ, tRetA, tSaleQ - tRetQ, tNetAmt,
                null, totalPurchase, totalProfit, totalMargin);
        return new NetSalesResponse(from, to, filter, rows, total);
    }

    private static long num(Object o) {
        return (o == null) ? 0L : ((Number) o).longValue();
    }

    /**
     * 매출액명세서. 근거: 레거시 매출액명세서.vb rollup(left(catCode,1), catCode, bookCode).
     * flat 도서집계를 상세→소계(분류)→분류계(대분류)→총계 순 계층으로 조립.
     * 합계=금액+세액(레거시 totalAmt2 미사용 규칙). category=null이면 전체(매출·무가·반품).
     */
    @Transactional(readOnly = true)
    public SalesStatementResponse statement(LocalDate from, LocalDate to, SalesCategory category) {
        List<SalesStatementAgg> aggs = saleRepository.statementAgg(from, to, category);
        List<SalesStatementRow> rows = new ArrayList<>();

        long gQty = 0, gAmt = 0, gTax = 0;                 // 총계
        String curMajor = null;
        boolean majorOpen = false;
        long mQty = 0, mAmt = 0, mTax = 0;                 // 대분류계
        String curCat = null, curCatName = null;
        boolean catOpen = false;
        long cQty = 0, cAmt = 0, cTax = 0;                 // 분류 소계

        for (SalesStatementAgg a : aggs) {
            String major = majorOf(a.getCatCode());
            String cat = a.getCatCode();

            if (catOpen && !java.util.Objects.equals(cat, curCat)) {
                rows.add(SalesStatementRow.catSubtotal(curMajor, curCat, curCatName, cQty, cAmt, cTax));
                catOpen = false;
            }
            if (majorOpen && !java.util.Objects.equals(major, curMajor)) {
                rows.add(SalesStatementRow.majorTotal(curMajor, mQty, mAmt, mTax));
                majorOpen = false;
            }
            if (!majorOpen) {
                curMajor = major;
                mQty = mAmt = mTax = 0;
                majorOpen = true;
            }
            if (!catOpen) {
                curCat = cat;
                curCatName = a.getCatName();
                cQty = cAmt = cTax = 0;
                catOpen = true;
            }

            rows.add(SalesStatementRow.detail(major, cat, a.getCatName(),
                    a.getBookCode(), a.getBookName(), a.getQty(), a.getAmount(), a.getTax()));

            cQty += a.getQty(); cAmt += a.getAmount(); cTax += a.getTax();
            mQty += a.getQty(); mAmt += a.getAmount(); mTax += a.getTax();
            gQty += a.getQty(); gAmt += a.getAmount(); gTax += a.getTax();
        }
        if (catOpen) {
            rows.add(SalesStatementRow.catSubtotal(curMajor, curCat, curCatName, cQty, cAmt, cTax));
        }
        if (majorOpen) {
            rows.add(SalesStatementRow.majorTotal(curMajor, mQty, mAmt, mTax));
        }
        rows.add(SalesStatementRow.grandTotal(gQty, gAmt, gTax));

        return new SalesStatementResponse(from, to, category == null ? null : category.name(), rows);
    }

    /** 대분류코드 = catCode 첫 글자. null/빈값은 미분류(null). */
    private static String majorOf(String catCode) {
        return (catCode == null || catCode.isEmpty()) ? null : catCode.substring(0, 1);
    }

    /**
     * 월별매출액명세서(37p): 대분류×상품 성적처리/비처리 인원·금액 + 대분류계 + 총계 + 과세매출·부가세.
     * 매출(SALE)만. 인원=수량(모의고사=응시인원), 성적처리=proc_type GRADED(그 외=비처리).
     */
    @Transactional(readOnly = true)
    public MonthlyStatementResponse monthlyStatement(int year, int month) {
        List<MonthlyStatementResponse.Row> rows = new ArrayList<>();
        // 누계: [gradedQty, gradedAmt, ungradedQty, ungradedAmt, taxableAmt, vat]
        long[] major = new long[6];
        long[] grand = new long[6];
        String curMajor = null;
        boolean majorOpen = false;

        for (Object[] r : saleRepository.monthlyStatementAgg(year, month)) {
            String m = (r[0] == null) ? null : r[0].toString();  // LEFT()가 Character로 올 수 있어 toString
            long[] v = {num(r[6]), num(r[7]), num(r[8]), num(r[9]), num(r[10]), num(r[11])};

            if (majorOpen && !java.util.Objects.equals(m, curMajor)) {
                rows.add(majorRow(MonthlyStatementResponse.Row.RowType.MAJOR_SUBTOTAL, curMajor, major));
                major = new long[6];
                majorOpen = false;
            }
            if (!majorOpen) {
                curMajor = m;
                majorOpen = true;
            }
            rows.add(new MonthlyStatementResponse.Row(
                    MonthlyStatementResponse.Row.RowType.DETAIL, m, (String) r[2], (String) r[4], (String) r[5],
                    v[0], v[1], v[2], v[3], v[0] + v[2], v[1] + v[3], v[4], v[5]));
            for (int i = 0; i < 6; i++) {
                major[i] += v[i];
                grand[i] += v[i];
            }
        }
        if (majorOpen) {
            rows.add(majorRow(MonthlyStatementResponse.Row.RowType.MAJOR_SUBTOTAL, curMajor, major));
        }
        rows.add(majorRow(MonthlyStatementResponse.Row.RowType.GRAND_TOTAL, null, grand));
        return new MonthlyStatementResponse(year, month, rows);
    }

    /** 집계배열 v=[gradedQty,gradedAmt,ungradedQty,ungradedAmt,taxableAmt,vat] → 소계/총계 행. */
    private MonthlyStatementResponse.Row majorRow(MonthlyStatementResponse.Row.RowType type,
                                                  String majorCode, long[] v) {
        return new MonthlyStatementResponse.Row(type, majorCode, null, null, null,
                v[0], v[1], v[2], v[3], v[0] + v[2], v[1] + v[3], v[4], v[5]);
    }

    /**
     * 거래명세서. 근거: 레거시 UC_TabPages_ETC.vb 거래명세서 데이터셋(공급자/공급받는자 + 유가/무가 라인).
     * 공급자=자사(SupplierProperties, 단일법인 가정), 공급받는자=거래처. 취소 제외.
     * category=null이면 매출(SALE)+무가(FREE), 지정 시 해당 구분만(예: RETURN=반품명세서).
     * 유가(공급가액≠0)/무가(=0) 분리, 합계=공급가액+세액.
     */
    @Transactional(readOnly = true)
    public TransactionStatementResponse transactionStatement(Long partnerId, LocalDate from, LocalDate to,
                                                             SalesCategory category) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "거래처가 없습니다. id=" + partnerId));
        java.util.Collection<SalesCategory> cats = (category != null)
                ? List.of(category)
                : List.of(SalesCategory.SALE, SalesCategory.FREE);
        List<Sale> sales = saleRepository.statementLines(partnerId, from, to, cats);

        List<TransactionStatementResponse.Line> priced = new ArrayList<>();
        List<TransactionStatementResponse.Line> free = new ArrayList<>();
        long pQty = 0, pSupply = 0, pTax = 0, fQty = 0;
        int pSeq = 0, fSeq = 0;

        for (Sale s : sales) {
            long supply = s.getSupplyAmount() == null ? 0 : s.getSupplyAmount();
            long tax = s.getTax() == null ? 0 : s.getTax();
            Product p = s.getProduct();
            String label = (p.getCatName() == null || p.getCatName().isEmpty())
                    ? p.getName() : p.getCatName() + " / " + p.getName();
            long unit = (s.getQty() != 0) ? supply / s.getQty() : 0;
            String cat = s.getSalesCategory().name();

            if (supply != 0) {
                priced.add(new TransactionStatementResponse.Line(++pSeq, label, p.getCode(), s.getQty(),
                        s.getUnitPrice(), s.getSupplyRate(), unit, supply, tax, cat, s.getMemo()));
                pQty += s.getQty();
                pSupply += supply;
                pTax += tax;
            } else {
                free.add(new TransactionStatementResponse.Line(++fSeq, label, p.getCode(), s.getQty(),
                        s.getUnitPrice(), s.getSupplyRate(), 0, 0, 0, cat, s.getMemo()));
                fQty += s.getQty();
            }
        }

        TransactionStatementResponse.Totals totals =
                new TransactionStatementResponse.Totals(pQty, pSupply, pTax, pSupply + pTax, fQty);
        TransactionStatementResponse.Party provider = new TransactionStatementResponse.Party(
                null, supplier.name(), supplier.bizNo(), supplier.bossName(),
                supplier.addr(), supplier.bizStatus(), supplier.bizItem());
        TransactionStatementResponse.Party receiver = new TransactionStatementResponse.Party(
                partner.getCode(), partner.getName(), partner.getBizNo(), partner.getBossName(),
                joinAddr(partner.getAddr1(), partner.getAddr2()), partner.getBizStatus(), partner.getBizItem());

        return new TransactionStatementResponse(from, to, category == null ? null : category.name(),
                provider, receiver, priced, free, totals);
    }

    private static String joinAddr(String a1, String a2) {
        if (a1 == null || a1.isEmpty()) {
            return a2;
        }
        return (a2 == null || a2.isEmpty()) ? a1 : a1 + " " + a2;
    }

    /**
     * 도서입출고현황. 근거: 레거시 도서입출고현황.vb(매입+매출 이중장부 종합).
     * 매입측(입고/취소, inventory_txn 원가) + 매출측(출고/반품, sales 공급가) + 정본 재고(종료일 기준)를 상품키로 병합.
     * 취소=매입취소(INBOUND 역분개, 현재 미모델링이면 0), 매출총이익=실판매−실매입.
     * 매입 거래처≠매출 거래처(이중장부)라 거래처 그룹은 제외(도서 단위). catCode·productId 옵션 필터.
     */
    @Transactional(readOnly = true)
    public BookInoutResponse bookInout(LocalDate from, LocalDate to, String catCode, Long productId) {
        // 매출측(상품별 출고/반품) → productId 맵
        Map<Long, BookSalesAgg> salesByProduct = new HashMap<>();
        for (BookSalesAgg a : saleRepository.bookSalesAgg(from, to)) {
            salesByProduct.put(a.getProductId(), a);
        }

        List<BookInoutResponse.Row> rows = new ArrayList<>();
        for (Object[] r : inventoryTxnRepository.bookPurchaseAgg(from, to, catCode, productId)) {
            long pid = ((Number) r[0]).longValue();
            long inQty = num(r[6]);
            long inAmt = num(r[7]);
            long cancQty = num(r[8]);
            long cancAmt = num(r[9]);
            long stockQty = num(r[10]);

            BookSalesAgg s = salesByProduct.get(pid);
            long outQty = (s == null) ? 0 : s.getOutQty();
            long outAmt = (s == null) ? 0 : s.getOutAmt();
            long retQty = (s == null) ? 0 : s.getRetQty();
            long retAmt = (s == null) ? 0 : s.getRetAmt();

            // 움직임·재고 모두 없으면 스킵
            if (inQty == 0 && cancQty == 0 && outQty == 0 && retQty == 0 && stockQty == 0) {
                continue;
            }

            Double cancelRate = (inQty == 0) ? null : round2(cancQty * 100.0 / inQty);
            Double returnRate = (outQty == 0) ? null : round2(retQty * 100.0 / outQty);
            long netPurQty = inQty - cancQty;
            long netPurAmt = inAmt - cancAmt;
            long netSalesQty = outQty - retQty;
            long netSalesAmt = outAmt - retAmt;

            rows.add(new BookInoutResponse.Row(
                    pid, (String) r[1], (String) r[2], (String) r[3], (String) r[4],
                    (r[5] == null) ? null : ((Number) r[5]).intValue(),
                    inQty, inAmt, cancQty, cancAmt, cancelRate, netPurQty, netPurAmt,
                    outQty, outAmt, retQty, retAmt, returnRate, netSalesQty, netSalesAmt,
                    stockQty, netSalesAmt - netPurAmt));
        }
        return new BookInoutResponse(from, to, catCode, rows);
    }

    private static Double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * 거래처별 매출대비표(당해 vs 전년 동기간). 근거: 레거시 거래처별_매출대비표.vb.
     * 전년은 salesDate 기준 [from−1년, to−1년](우리 catCode엔 연도 미인코딩 → 날짜 비교). SALE만.
     * groupBy=PARTNER(거래처)/CATEGORY(거래처×분류)/BOOK(거래처×도서). 비율=당해÷전년×100.
     */
    @Transactional(readOnly = true)
    public YoyComparisonResponse yoyComparison(LocalDate from, LocalDate to,
                                               YoyComparisonResponse.GroupBy groupBy,
                                               Long partnerId, String catCode) {
        LocalDate prevFrom = from.minusYears(1);
        LocalDate prevTo = to.minusYears(1);
        List<PartnerProductSalesAgg> curList = saleRepository.salesByPartnerProduct(from, to, partnerId, catCode);
        List<PartnerProductSalesAgg> prevList = saleRepository.salesByPartnerProduct(prevFrom, prevTo, partnerId, catCode);

        Map<String, PartnerProductSalesAgg> labels = new HashMap<>();
        Map<String, long[]> cur = accumulate(curList, groupBy, labels);
        Map<String, long[]> prev = accumulate(prevList, groupBy, labels);

        List<YoyComparisonResponse.Row> rows = new ArrayList<>();
        java.util.Set<String> keys = new java.util.HashSet<>(cur.keySet());
        keys.addAll(prev.keySet());
        for (String k : keys) {
            long[] c = cur.getOrDefault(k, new long[2]);
            long[] p = prev.getOrDefault(k, new long[2]);
            PartnerProductSalesAgg lb = labels.get(k);
            boolean withCat = groupBy != YoyComparisonResponse.GroupBy.PARTNER;
            boolean withBook = groupBy == YoyComparisonResponse.GroupBy.BOOK;
            rows.add(new YoyComparisonResponse.Row(
                    lb.getPartnerId(), lb.getPartnerCode(), lb.getPartnerName(),
                    withCat ? lb.getCatCode() : null, withCat ? lb.getCatName() : null,
                    withBook ? lb.getBookCode() : null, withBook ? lb.getBookName() : null,
                    c[0], c[1], p[0], p[1],
                    c[0] - p[0], c[1] - p[1],
                    p[0] == 0 ? null : round2(c[0] * 100.0 / p[0]),
                    p[1] == 0 ? null : round2(c[1] * 100.0 / p[1])));
        }
        rows.sort(java.util.Comparator
                .comparing(YoyComparisonResponse.Row::partnerCode, java.util.Comparator.nullsLast(String::compareTo))
                .thenComparing(r -> r.catCode(), java.util.Comparator.nullsLast(String::compareTo))
                .thenComparing(r -> r.bookCode(), java.util.Comparator.nullsLast(String::compareTo)));

        return new YoyComparisonResponse(from, to, prevFrom, prevTo, groupBy, rows);
    }

    /** groupBy 수준으로 (qty,amount) 누적 + 라벨(첫 등장) 수집. */
    private static Map<String, long[]> accumulate(List<PartnerProductSalesAgg> list,
                                                  YoyComparisonResponse.GroupBy groupBy,
                                                  Map<String, PartnerProductSalesAgg> labels) {
        Map<String, long[]> map = new HashMap<>();
        for (PartnerProductSalesAgg a : list) {
            String key = switch (groupBy) {
                case PARTNER -> "P" + a.getPartnerId();
                case CATEGORY -> "P" + a.getPartnerId() + "|C" + (a.getCatCode() == null ? "" : a.getCatCode());
                case BOOK -> "P" + a.getPartnerId() + "|B" + a.getProductId();
            };
            long[] acc = map.computeIfAbsent(key, x -> new long[2]);
            acc[0] += a.getQty();
            acc[1] += a.getAmount();
            labels.putIfAbsent(key, a);
        }
        return map;
    }

    /**
     * 과목별매출현황. 근거: 레거시 과목별매출현황.vb(거래처×분류×도서 수량·반품률).
     * 순매출수량=매출−반품, 반품률(%)=반품÷매출×100(매출0이면 null, 소수 2자리). 취소 제외.
     */
    @Transactional(readOnly = true)
    public CategorySalesResponse categorySales(LocalDate from, LocalDate to, Long partnerId, String catCode) {
        List<CategorySalesAgg> aggs = saleRepository.categorySales(from, to, partnerId, catCode);
        List<CategorySalesResponse.Row> rows = new ArrayList<>(aggs.size());
        for (CategorySalesAgg a : aggs) {
            long sale = a.getSaleQty();
            long ret = a.getReturnQty();
            Double rate = (sale == 0) ? null : Math.round(ret * 100.0 / sale * 100.0) / 100.0;
            rows.add(new CategorySalesResponse.Row(
                    a.getPartnerId(), a.getPartnerCode(), a.getPartnerName(),
                    a.getCatCode(), a.getCatName(), a.getBookCode(), a.getBookName(),
                    sale, ret, sale - ret, a.getTeacherQty(), rate));
        }
        return new CategorySalesResponse(from, to, partnerId, catCode, rows);
    }
}
