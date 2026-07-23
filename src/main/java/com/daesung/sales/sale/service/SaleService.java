package com.daesung.sales.sale.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.closing.config.SupplierProperties;
import com.daesung.sales.closing.service.PeriodLockService;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.inventory.entity.TxnType;
import com.daesung.sales.inventory.repository.InventoryTxnRepository;
import com.daesung.sales.inventory.service.InventoryService;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductRepository;
import com.daesung.sales.sale.dto.CategorySalesAgg;
import com.daesung.sales.sale.dto.CategorySalesResponse;
import com.daesung.sales.sale.dto.SaleResponse;
import com.daesung.sales.sale.dto.SalesEntryRequest;
import com.daesung.sales.sale.dto.SalesEntryResponse;
import com.daesung.sales.sale.dto.NetSalesResponse;
import com.daesung.sales.sale.dto.SalesStatementAgg;
import com.daesung.sales.sale.dto.SalesStatementResponse;
import com.daesung.sales.sale.dto.SalesStatementRow;
import com.daesung.sales.sale.dto.TransactionStatementResponse;
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
    private final OutTypeLookupService outTypeLookupService;
    private final WarehouseRepository warehouseRepository;
    private final InventoryService inventoryService;
    private final InventoryTxnRepository inventoryTxnRepository;
    private final PeriodLockService periodLockService;
    private final SupplierProperties supplier;

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

            long supplyAmount = (long) ((double) item.unitPrice() * item.supplyRate() / 100.0 * item.qty());
            long tax = product.isTaxFree() ? 0L : supplyAmount / 10L;
            long totalAmount = supplyAmount + tax;

            String salesNo = "I-" + datePart + "-" + saleRepository.nextInvoiceSeq();

            Sale sale = Sale.create(salesNo, req.salesDate(), partner, product,
                    SalesType.NORMAL_SALES, item.shipmentType(), salesCategory,
                    item.unitPrice(), item.supplyRate(), item.qty(),
                    supplyAmount, tax, totalAmount, item.memo());
            saleRepository.save(sale);

            // 재고 반영(한 트랜잭션): 출고유형 → 부호/이벤트유형. 위탁·취소는 이 API 불가.
            int delta = stockDelta(item.shipmentType(), item.qty());
            TxnType txnType = (delta >= 0) ? TxnType.RETURN : TxnType.OUTBOUND;
            int stockBalance = inventoryService.applyShipment(product, warehouse, delta, txnType,
                    item.shipmentType(), req.salesDate(), salesNo, item.memo());

            lines.add(new SalesEntryResponse.Line(salesNo, product.getId(), product.getCode(),
                    item.shipmentType(), salesCategory, item.qty(), supplyAmount, tax, totalAmount, stockBalance));
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
     * 콘텐츠구분 순매출. SELF=매출−반품, EXTERNAL=매출−매입=이익(매입원가=입고 unit_cost 가중평균).
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
