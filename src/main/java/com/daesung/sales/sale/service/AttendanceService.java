package com.daesung.sales.sale.service;

import com.daesung.sales.sale.dto.AttendanceAgg;
import com.daesung.sales.sale.dto.AttendanceResponse;
import com.daesung.sales.sale.repository.SaleRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 응시현황(연도별). 근거: 레거시 응시현황.vb — 거래처별 월 수량·매출 크로스탭 + 지역구분 rollup.
 *
 * <p>발주처 회신(자료요청서 2-2): 레거시 화면이 "2022년까지만 조회 가능"으로 막혀 있고
 * 전산담당자가 부재라, <b>매출 데이터로 다시 만들어 달라</b>는 요청이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private static final int MONTHS = 12;

    private final SaleRepository saleRepository;

    public AttendanceResponse yearly(int year, String grade, String productType) {
        // 거래처별로 12개월 칸을 채운다(빈 달은 0).
        Map<String, Bucket> byPartner = new LinkedHashMap<>();
        for (AttendanceAgg a : saleRepository.attendanceYearly(year, blankToNull(grade),
                blankToNull(productType))) {
            Bucket b = byPartner.computeIfAbsent(a.getPartnerCode(),
                    k -> new Bucket(regionGroup(a.getPartnerCode(), a.getCityName()),
                            a.getPartnerCode(), a.getPartnerName(), a.getCityName()));
            int idx = a.getMonth() - 1;
            b.qty[idx] += (a.getQty() == null) ? 0 : a.getQty();
            b.amount[idx] += (a.getAmount() == null) ? 0 : a.getAmount();
        }

        // 지역구분 → 거래처 순서로 묶어 소계·총계를 만든다(레거시 rollup 순서와 같다).
        Map<String, List<Bucket>> byRegion = new LinkedHashMap<>();
        byPartner.values().stream()
                .sorted((x, y) -> {
                    int c = x.regionGroup.compareTo(y.regionGroup);
                    return (c != 0) ? c : x.partnerCode.compareTo(y.partnerCode);
                })
                .forEach(b -> byRegion.computeIfAbsent(b.regionGroup, k -> new ArrayList<>()).add(b));

        List<AttendanceResponse.Row> rows = new ArrayList<>();
        Bucket grand = new Bucket("", null, null, null);
        for (Map.Entry<String, List<Bucket>> e : byRegion.entrySet()) {
            Bucket sub = new Bucket(e.getKey(), null, null, null);
            for (Bucket b : e.getValue()) {
                rows.add(b.toRow("PARTNER"));
                sub.add(b);
                grand.add(b);
            }
            rows.add(sub.toRow("REGION_SUBTOTAL"));
        }
        rows.add(grand.toRow("TOTAL"));
        return new AttendanceResponse(year, rows);
    }

    /**
     * 지역구분. <b>레거시 규칙을 그대로 옮겼다</b>(응시현황.vb:337~339) —
     * <pre>
     *   custCode 앞 1자리가 '2'  → 특약점_ + 앞2자리 + 도시명
     *   custCode &lt; '90001'      → 특약점_ + 앞1자리 + 도시명
     *   그 외                     → 특약점외
     * </pre>
     * 거래처코드가 숫자 5자리라는 전제의 규칙이라, 형식이 다르면 '특약점외'로 떨어진다
     * (실데이터는 '00001'~'90001' 형식이다).
     */
    private static String regionGroup(String custCode, String city) {
        String c = (custCode == null) ? "" : custCode;
        String cityPart = (city == null) ? "" : city;
        if (c.startsWith("2") && c.length() >= 2) {
            return "특약점_" + c.substring(0, 2) + cityPart;
        }
        if (!c.isEmpty() && c.compareTo("90001") < 0) {
            return "특약점_" + c.charAt(0) + cityPart;
        }
        return "특약점외";
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    /** 12개월 누적 통. 소계·총계도 같은 통을 재사용해 합산 규칙이 갈리지 않게 한다. */
    private static final class Bucket {
        private final String regionGroup;
        private final String partnerCode;
        private final String partnerName;
        private final String cityName;
        private final long[] qty = new long[MONTHS];
        private final long[] amount = new long[MONTHS];

        private Bucket(String regionGroup, String partnerCode, String partnerName, String cityName) {
            this.regionGroup = regionGroup;
            this.partnerCode = partnerCode;
            this.partnerName = partnerName;
            this.cityName = cityName;
        }

        private void add(Bucket o) {
            for (int i = 0; i < MONTHS; i++) {
                qty[i] += o.qty[i];
                amount[i] += o.amount[i];
            }
        }

        private AttendanceResponse.Row toRow(String rowType) {
            List<Long> q = new ArrayList<>(MONTHS);
            List<Long> a = new ArrayList<>(MONTHS);
            long tq = 0;
            long ta = 0;
            for (int i = 0; i < MONTHS; i++) {
                q.add(qty[i]);
                a.add(amount[i]);
                tq += qty[i];
                ta += amount[i];
            }
            return new AttendanceResponse.Row(rowType, regionGroup, partnerCode, partnerName,
                    cityName, q, a, tq, ta);
        }
    }
}
