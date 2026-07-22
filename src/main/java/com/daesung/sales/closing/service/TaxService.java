package com.daesung.sales.closing.service;

import com.daesung.sales.closing.config.SupplierProperties;
import com.daesung.sales.closing.dto.RevenueReportResponse;
import com.daesung.sales.closing.dto.TaxInvoiceResponse;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.sale.repository.SaleRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
     * 계산서신고 데이터: 거래처×과세구분 단위로 계산서 1장(품목=도서별). 면세'05'/과세'01'.
     * 공급자(자사)는 설정 주입. 기간 미지정 시 올해 1/1~오늘, 작성일자=종료일(말일).
     */
    @Transactional(readOnly = true)
    public TaxInvoiceResponse taxInvoices(LocalDate fromDate, LocalDate toDate, Long partnerId) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();

        // (거래처, 과세구분) → 품목 라인 누적. LinkedHashMap로 조회 순서 유지.
        record Key(long partnerId, String taxBucket) {}
        Map<Key, List<TaxInvoiceResponse.Item>> itemsByKey = new LinkedHashMap<>();
        Map<Key, String> nameByKey = new LinkedHashMap<>();
        Map<Key, long[]> totalByKey = new LinkedHashMap<>(); // [supply, tax]

        for (Object[] r : saleRepository.taxInvoiceLines(from, to, partnerId)) {
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
    public byte[] exportTaxInvoices(LocalDate fromDate, LocalDate toDate, Long partnerId) {
        TaxInvoiceResponse data = taxInvoices(fromDate, toDate, partnerId);
        List<Long> ids = data.invoices().stream()
                .map(TaxInvoiceResponse.Invoice::partnerId)
                .distinct()
                .toList();
        Map<Long, Partner> partners = new LinkedHashMap<>();
        partnerRepository.findAllById(ids).forEach(p -> partners.put(p.getId(), p));
        return excelExporter.export(data, partners, supplier);
    }

    private static String normalizeTaxType(String taxType) {
        if (taxType == null || taxType.isBlank()) {
            return "ALL";
        }
        String t = taxType.trim().toUpperCase();
        return switch (t) {
            case "FREE", "TAXABLE", "ALL" -> t;
            default -> "ALL";
        };
    }

    private static long num(Object o) {
        return (o == null) ? 0L : ((Number) o).longValue();
    }
}
