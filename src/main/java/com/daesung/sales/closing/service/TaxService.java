package com.daesung.sales.closing.service;

import com.daesung.sales.closing.dto.RevenueReportResponse;
import com.daesung.sales.sale.repository.SaleRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 세무(마감관리). 수익신고 집계. 계산서신고(홈택스 export)는 후속. */
@Service
@RequiredArgsConstructor
public class TaxService {

    private final SaleRepository saleRepository;

    /**
     * 수익신고: 거래처×월 순매출/세액 집계 + 거래처 소계 + 전체 합계.
     * taxType: null/ALL=전체, FREE=면세(tax=0), TAXABLE=과세(tax≠0). 기간 미지정 시 올해 1/1~오늘.
     */
    @Transactional(readOnly = true)
    public RevenueReportResponse revenueReport(LocalDate fromDate, LocalDate toDate, String taxType) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();
        String filter = normalizeTaxType(taxType);

        // 거래처 순서 유지하며 월별 엔트리 누적.
        Map<Long, RevenueReportResponse.PartnerRevenue> byPartner = new LinkedHashMap<>();
        Map<Long, List<RevenueReportResponse.MonthEntry>> monthsByPartner = new LinkedHashMap<>();
        Map<Long, String> nameByPartner = new LinkedHashMap<>();
        Map<Long, long[]> subtotal = new LinkedHashMap<>(); // [count, supply, tax]

        for (Object[] r : saleRepository.revenueReport(from, to, filter)) {
            long pid = num(r[0]);
            String name = (String) r[1];
            String ym = (String) r[2];
            long cnt = num(r[3]);
            long supply = num(r[4]);
            long tax = num(r[5]);

            nameByPartner.putIfAbsent(pid, name);
            monthsByPartner.computeIfAbsent(pid, k -> new ArrayList<>())
                    .add(new RevenueReportResponse.MonthEntry(ym, cnt, supply, tax));
            long[] st = subtotal.computeIfAbsent(pid, k -> new long[3]);
            st[0] += cnt; st[1] += supply; st[2] += tax;
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
