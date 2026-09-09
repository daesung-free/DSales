package com.daesung.sales.closing.service;

import com.daesung.sales.closing.config.SupplierProperties;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.closing.dto.InvoiceAdjustmentResponse;
import com.daesung.sales.closing.dto.RevenueReportResponse;
import com.daesung.sales.closing.dto.TaxFilingResponse;
import com.daesung.sales.closing.dto.TaxInvoiceResponse;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.sale.repository.SaleRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 세무(마감관리). 수익신고 집계 + 계산서신고 데이터. 홈택스 파일 export는 후속. */
@Service
@RequiredArgsConstructor
public class TaxService {

    private final SaleRepository saleRepository;
    private final PartnerRepository partnerRepository;
    private final SupplierProperties supplier;
    private final TaxInvoiceExcelExporter excelExporter;

    /**
     * 수익신고: 거래처×월 순매출/세액 집계 + 거래처 소계 + 전체 합계.
     * taxType: null/ALL=전체, FREE=면세(tax=0), TAXABLE=과세(tax≠0). 기간 미지정 시 올해 1/1~오늘.
     */
    @Transactional(readOnly = true)
    public RevenueReportResponse revenueReport(LocalDate fromDate, LocalDate toDate, String taxType) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();
        String filter = normalizeTaxType(taxType);

        Map<Long, List<RevenueReportResponse.MonthEntry>> monthsByPartner = new LinkedHashMap<>();
        Map<Long, String> nameByPartner = new LinkedHashMap<>();
        Map<Long, long[]> subtotal = new LinkedHashMap<>(); // [count, supply, tax]

        for (Object[] r : saleRepository.revenueReport(from, to, filter)) {
            long pid = num(r[0]);
            nameByPartner.putIfAbsent(pid, (String) r[1]);
            monthsByPartner.computeIfAbsent(pid, k -> new ArrayList<>())
                    .add(new RevenueReportResponse.MonthEntry((String) r[2], num(r[3]), num(r[4]), num(r[5])));
            long[] st = subtotal.computeIfAbsent(pid, k -> new long[3]);
            st[0] += num(r[3]); st[1] += num(r[4]); st[2] += num(r[5]);
        }

        List<RevenueReportResponse.PartnerRevenue> rows = new ArrayList<>();
        long tCnt = 0, tSupply = 0, tTax = 0;
        for (Long pid : nameByPartner.keySet()) {
            long[] st = subtotal.get(pid);
            rows.add(new RevenueReportResponse.PartnerRevenue(
                    pid, nameByPartner.get(pid), st[0], st[1], st[2], monthsByPartner.get(pid)));
            tCnt += st[0]; tSupply += st[1]; tTax += st[2];
        }
        RevenueReportResponse.PartnerRevenue total = new RevenueReportResponse.PartnerRevenue(
                null, "합계", tCnt, tSupply, tTax, List.of());
        return new RevenueReportResponse(from, to, filter, rows, total);
    }

    /**
     * 계산서 반품/취소 10일 분기(재무팀 확정 2026-07-25): 반품 처리일이 매월 10일 이전이면
     * 당월 수정발행(AMEND), 10일 이후면 익월 정산 마이너스(NEXT_MONTH_MINUS)로 분류 + 반영 신고월 산출.
     */
    @Transactional(readOnly = true)
    public InvoiceAdjustmentResponse invoiceAdjustments(int year, int month) {
        List<InvoiceAdjustmentResponse.Row> rows = new ArrayList<>();
        long amendSupply = 0, amendTax = 0, nextSupply = 0, nextTax = 0;

        for (Object[] r : saleRepository.returnsInMonth(year, month)) {
            LocalDate returnDate = ((java.sql.Date) r[3]).toLocalDate();
            long supply = num(r[4]), tax = num(r[5]);

            boolean amend = returnDate.getDayOfMonth() <= 10;   // 발행기준일=10일
            InvoiceAdjustmentResponse.Mode mode = amend
                    ? InvoiceAdjustmentResponse.Mode.AMEND
                    : InvoiceAdjustmentResponse.Mode.NEXT_MONTH_MINUS;
            // 반영 신고월: 10일 이전=당월, 이후=익월
            LocalDate reporting = amend ? returnDate.withDayOfMonth(1)
                    : returnDate.withDayOfMonth(1).plusMonths(1);
            String reportingMonth = String.format("%d%02d", reporting.getYear(), reporting.getMonthValue());

            rows.add(new InvoiceAdjustmentResponse.Row(
                    (String) r[0], (String) r[1], (String) r[2], returnDate, supply, tax, mode, reportingMonth));
            if (amend) {
                amendSupply += supply;
                amendTax += tax;
            } else {
                nextSupply += supply;
                nextTax += tax;
            }
        }
        var summary = new InvoiceAdjustmentResponse.Summary(amendSupply, amendTax, nextSupply, nextTax);
        return new InvoiceAdjustmentResponse(year, month, rows, summary);
    }

    /**
     * 계산서·세금계산서 월별신고(38p): 월×발행유형(계산서=면세/세금계산서=과세)으로 매출·반품·순매출·세액 집계.
     * 발행유형은 sale.tax(0/≠0)로 파생(신규 필드 없음). 미발행분은 정의 미확정→0 placeholder.
     */
    @Transactional(readOnly = true)
    public TaxFilingResponse taxFiling(int year) {
        // month → [invoiceSale, invoiceReturn, taxInvoiceSale, taxInvoiceReturn, tax]
        Map<Integer, long[]> byMonth = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            byMonth.put(m, new long[5]);
        }
        for (Object[] r : saleRepository.taxFilingByMonth(year)) {
            int month = (int) num(r[0]);
            boolean invoice = "INVOICE".equals(r[1]);   // 계산서(면세)
            long saleSupply = num(r[2]), returnSupply = num(r[3]), netTax = num(r[4]);
            long[] acc = byMonth.get(month);
            if (invoice) {
                acc[0] += saleSupply;
                acc[1] += returnSupply;
            } else {
                acc[2] += saleSupply;
                acc[3] += returnSupply;
            }
            acc[4] += netTax;
        }

        List<TaxFilingResponse.MonthRow> rows = new ArrayList<>();
        long[] tot = new long[5];
        for (int m = 1; m <= 12; m++) {
            long[] a = byMonth.get(m);
            rows.add(monthRow(m, a));
            for (int i = 0; i < 5; i++) {
                tot[i] += a[i];
            }
        }
        return new TaxFilingResponse(year, rows, monthRow(0, tot));
    }

    /** acc=[invoiceSale, invoiceReturn, taxInvoiceSale, taxInvoiceReturn, tax] → 응답행(순매출·계 파생). */
    private TaxFilingResponse.MonthRow monthRow(int month, long[] a) {
        long invNet = a[0] - a[1];
        long taxNet = a[2] - a[3];
        return new TaxFilingResponse.MonthRow(
                month, a[0], 0L, a[2], 0L, a[1], a[3], invNet, taxNet, invNet + taxNet, a[4]);
    }

    /**
     * 계산서신고 데이터: 거래처×과세구분 단위로 계산서 1장(품목=도서별). 면세'05'/과세'01'.
     * 공급자(자사)는 설정 주입. 기간 미지정 시 올해 1/1~오늘, 작성일자=종료일(말일).
     */
    @Transactional(readOnly = true)
    public TaxInvoiceResponse taxInvoices(LocalDate fromDate, LocalDate toDate, Long partnerId,
                                          String taxType) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();
        String filter = normalizeTaxType(taxType);

        // (거래처, 과세구분) → 품목 라인 누적. LinkedHashMap로 조회 순서 유지.
        record Key(long partnerId, String taxBucket) {}
        Map<Key, List<TaxInvoiceResponse.Item>> itemsByKey = new LinkedHashMap<>();
        Map<Key, String> nameByKey = new LinkedHashMap<>();
        Map<Key, long[]> totalByKey = new LinkedHashMap<>(); // [supply, tax]

        for (Object[] r : saleRepository.taxInvoiceLines(from, to, partnerId, filter)) {
            Key key = new Key(num(r[0]), (String) r[4]);
            nameByKey.putIfAbsent(key, (String) r[1]);
            itemsByKey.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new TaxInvoiceResponse.Item((String) r[3], num(r[5]), num(r[6])));
            long[] t = totalByKey.computeIfAbsent(key, k -> new long[2]);
            t[0] += num(r[5]); t[1] += num(r[6]);
        }

        List<TaxInvoiceResponse.Invoice> invoices = new ArrayList<>();
        for (Key key : nameByKey.keySet()) {
            boolean free = "FREE".equals(key.taxBucket());
            List<TaxInvoiceResponse.Item> items = itemsByKey.get(key);
            long[] tot = totalByKey.get(key);
            long itemSupplySum = items.stream().mapToLong(TaxInvoiceResponse.Item::supply).sum();

            Partner p = partnerRepository.findById(key.partnerId()).orElse(null);
            invoices.add(new TaxInvoiceResponse.Invoice(
                    free ? "05" : "01",
                    key.taxBucket(),
                    supplier.name(), supplier.bizNo(),
                    key.partnerId(),
                    (p != null) ? p.getName() : nameByKey.get(key),
                    (p != null) ? p.getBizNo() : null,
                    (p != null) ? p.getBossName() : null,
                    items, tot[0], tot[1],
                    itemSupplySum == tot[0]));
        }
        return new TaxInvoiceResponse(to, invoices, invoices.size());
    }

    /** 계산서 데이터를 홈택스 대량발행 xlsx로 export. 공급받는자 전체 세무정보를 Partner에서 로드. */
    @Transactional(readOnly = true)
    public byte[] exportTaxInvoices(LocalDate fromDate, LocalDate toDate, Long partnerId,
                                    String taxType) {
        // ★화면과 같은 필터로 만든다. 다운로드만 조건이 빠지면 화면에 없던 건이 파일에 실린다.
        TaxInvoiceResponse data = taxInvoices(fromDate, toDate, partnerId, taxType);
        List<Long> ids = data.invoices().stream()
                .map(TaxInvoiceResponse.Invoice::partnerId)
                .distinct()
                .toList();
        Map<Long, Partner> partners = new LinkedHashMap<>();
        partnerRepository.findAllById(ids).forEach(p -> partners.put(p.getId(), p));
        return excelExporter.export(data, partners, supplier);
    }

    /**
     * 과세구분 필터 정규화. 미지정(null/공백)은 <b>전체</b>다.
     *
     * <p>★<b>모르는 값은 조용히 전체로 넘기지 않는다.</b> 예전에는 그랬는데,
     * 그러면 {@code taxType=TAXABL} 같은 오타 하나에 <b>과세·면세가 뒤섞인 목록</b>이
     * 걸러진 결과인 척 돌아온다. 세무 신고에 쓰는 숫자라 조용한 실패가 가장 위험하다
     * (개발팀 점검 2026-09-09 P0-4가 정확히 이 증상을 지적했다).
     */
    private static String normalizeTaxType(String taxType) {
        if (taxType == null || taxType.isBlank()) {
            return "ALL";
        }
        String t = taxType.trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "FREE", "TAXABLE", "ALL" -> t;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "과세구분 값을 알 수 없습니다: " + taxType + " (FREE=면세 / TAXABLE=과세 / 미지정=전체)");
        };
    }

    private static long num(Object o) {
        return (o == null) ? 0L : ((Number) o).longValue();
    }
}
