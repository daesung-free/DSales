package com.daesung.sales.sale.service;

import com.daesung.sales.closing.config.SupplierProperties;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.inventory.repository.InventoryTxnRepository;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.sale.dto.BookInoutResponse;
import com.daesung.sales.sale.dto.BookSalesAgg;
import com.daesung.sales.sale.dto.CategorySalesAgg;
import com.daesung.sales.sale.dto.CategorySalesResponse;
import com.daesung.sales.sale.dto.RoundWorkStatusRow;
import com.daesung.sales.sale.dto.MonthlyStatementResponse;
import com.daesung.sales.sale.dto.NetSalesResponse;
import com.daesung.sales.sale.dto.PartnerProductSalesAgg;
import com.daesung.sales.sale.dto.SalesStatementAgg;
import com.daesung.sales.sale.dto.SalesStatementResponse;
import com.daesung.sales.sale.dto.SalesStatementRow;
import com.daesung.sales.sale.dto.SalesSummaryResponse;
import com.daesung.sales.sale.dto.SalesSummaryRow;
import com.daesung.sales.sale.dto.TransactionStatementResponse;
import com.daesung.sales.sale.dto.YoyComparisonResponse;
import com.daesung.sales.sale.entity.Sale;
import com.daesung.sales.sale.repository.SaleRepository;
import com.daesung.sales.salestype.entity.SalesCategory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매출 리포트 집계 8종(레거시 RDLC 재현). 전부 읽기전용 — {@link SaleService}(쓰기 도메인)에서 분리.
 * 순매출/명세서/월별명세서/거래명세서/도서입출고/매출대비표/과목별매출. 이중장부(sales+inventory_txn) 조회.
 */
@Service
@RequiredArgsConstructor
public class SaleReportService {

    private final SaleRepository saleRepository;
    private final InventoryTxnRepository inventoryTxnRepository;
    private final PartnerRepository partnerRepository;
    private final SupplierProperties supplier;

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
        String filter = (contentType == null || contentType.isBlank())
                ? null : contentType.trim().toUpperCase(Locale.ROOT);

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
                    saleQty, saleAmt, freeAmt, retQty, returnRate(retQty, saleQty), retAmt, netQty, netAmt,
                    unitCost, purchase, profit, margin));
            tSaleQ += saleQty; tSaleA += saleAmt; tFreeA += freeAmt; tRetQ += retQty; tRetA += retAmt;
        }

        long tNetAmt = tSaleA - tRetA;
        Long totalPurchase = anyExternal ? tPurch : null;
        Long totalProfit = anyExternal ? (tNetAmt - tPurch) : null;
        Double totalMargin = (anyExternal && tNetAmt != 0) ? Math.round((double) totalProfit / tNetAmt * 100 * 10) / 10.0 : null;
        NetSalesResponse.Row total = new NetSalesResponse.Row(null, "합계", null, null,
                tSaleQ, tSaleA, tFreeA, tRetQ, returnRate(tRetQ, tSaleQ), tRetA, tSaleQ - tRetQ, tNetAmt,
                null, totalPurchase, totalProfit, totalMargin);
        return new NetSalesResponse(from, to, filter, rows, total);
    }

    /** 반품률 %(반품/매출). 매출이 0이면 나눌 수 없어 null — 0%로 두면 "반품 없음"으로 오해된다. */
    private static Double returnRate(long returnQty, long saleQty) {
        return (saleQty == 0) ? null : Math.round((double) returnQty / saleQty * 100 * 10) / 10.0;
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
        // 실물 양식 거래정보 블록의 "학교(원)" 칸. 한 명세서는 보통 한 학교 건이라 첫 값을 대표로 쓴다.
        String schoolName = null;

        for (Sale s : sales) {
            long supply = s.getSupplyAmount() == null ? 0 : s.getSupplyAmount();
            long tax = s.getTax() == null ? 0 : s.getTax();
            Product p = s.getProduct();
            String label = (p.getCatName() == null || p.getCatName().isEmpty())
                    ? p.getName() : p.getCatName() + " / " + p.getName();
            long unit = (s.getQty() != 0) ? supply / s.getQty() : 0;
            String cat = s.getSalesCategory().name();
            if (schoolName == null && s.getSchoolName() != null && !s.getSchoolName().isBlank()) {
                schoolName = s.getSchoolName();
            }

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

        // 실물 양식 상단 "23 권" = 유가+무가 전체 수량(2026-08-05 거래명세서 실물 대조).
        TransactionStatementResponse.Totals totals = new TransactionStatementResponse.Totals(
                pQty, pSupply, pTax, pSupply + pTax, fQty, pQty + fQty);
        TransactionStatementResponse.Party provider = new TransactionStatementResponse.Party(
                null, supplier.name(), supplier.bizNo(), supplier.bossName(),
                supplier.addr(), supplier.bizStatus(), supplier.bizItem(), supplier.tel());
        TransactionStatementResponse.Party receiver = new TransactionStatementResponse.Party(
                partner.getCode(), partner.getName(), partner.getBizNo(), partner.getBossName(),
                joinAddr(partner.getAddr1(), partner.getAddr2()), partner.getBizStatus(),
                partner.getBizItem(), null);   // 실물 양식은 공급자 전화만 표기

        return new TransactionStatementResponse(from, to, category == null ? null : category.name(),
                provider, receiver, schoolName, priced, free, totals);
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

    /**
     * 회차별 작업현황(구 IC회차별작업현황, 물류). 근거: 레거시 IC회차별작업현황.vb.
     * 분류×도서×회차 단위로 포장구분(개별1/개별2/반별) 수량을 펼쳐 보여준다.
     * 레거시와 동일하게 회차 없는 건(bookReqSeq=0)은 제외한다.
     */
    @Transactional(readOnly = true)
    public List<RoundWorkStatusRow> roundWorkStatus(LocalDate from, LocalDate to, String catCode) {
        List<Object[]> raw = saleRepository.roundWorkStatus(from, to,
                (catCode == null || catCode.isBlank()) ? null : catCode);
        List<RoundWorkStatusRow> rows = new ArrayList<>(raw.size());
        for (Object[] r : raw) {
            rows.add(new RoundWorkStatusRow(
                    (String) r[0], (String) r[1], (String) r[2], (String) r[3],
                    (r[4] == null) ? null : ((Number) r[4]).intValue(),
                    ((Number) r[5]).longValue(), ((Number) r[6]).longValue(),
                    ((Number) r[7]).longValue(), ((Number) r[8]).longValue()));
        }
        return rows;
    }
}
