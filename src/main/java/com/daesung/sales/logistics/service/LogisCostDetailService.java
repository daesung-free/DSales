package com.daesung.sales.logistics.service;

import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.dsre.gateway.LogisCostDetailRow;
import com.daesung.sales.dsre.gateway.LogisMode;
import com.daesung.sales.logistics.dto.LogisCostDetailResponse;
import com.daesung.sales.logistics.dto.LogisCostDetailResponse.Grain;
import com.daesung.sales.logistics.dto.LogisCostDetailResponse.Row;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 물류 작업비 명세(28p) 조립 — 행 + 소계.
 *
 * <p>게이트웨이가 가장 잘게(상품×학년×시행×신청×거래처) 낸 것을 여기서 <b>접는다</b>.
 * 레거시가 체크박스로 신청/거래처 축을 갈아 끼우던 것을 {@link Grain}으로 받는다.
 * 쿼리를 모드마다 두지 않는 이유는 물류비 계산식이 두 벌로 갈라지지 않게 하기 위함이다.
 */
@Service
// DSRE2 단가·자재를 직접 읽는 서비스라 연동이 꺼지면 뜰 수 없다(LogisticsController와 같은 조건).
// 조건을 안 걸면 게이트웨이 빈이 없어 컨텍스트 전체가 깨진다.
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
@RequiredArgsConstructor
public class LogisCostDetailService {

    private final DsreGateway dsreGateway;

    public LogisCostDetailResponse outboundDetail(LocalDate from, LocalDate to, LogisMode mode,
                                                  boolean includeCancel, Grain grain) {
        Grain g = (grain == null) ? Grain.PARTNER : grain;   // 레거시 기본은 '상세보기' 꺼짐
        List<LogisCostDetailRow> src = dsreGateway.outboundDetail(from, to, mode, includeCancel);

        // 1) 요청한 축으로 접는다. 신청 축이면 원본 그대로, 거래처 축이면 신청을 합친다.
        Map<String, Acc> folded = new LinkedHashMap<>();
        for (LogisCostDetailRow r : src) {
            String key = r.productCode() + "|" + nz(r.grade()) + "|" + r.dtlCd() + "|"
                    + (g == Grain.REQUEST ? r.reqCd() : nz(r.partnerCode()));
            folded.computeIfAbsent(key, k -> new Acc(r, g)).add(r);
        }

        // 2) 소계를 붙인다(레거시 ROLLUP 순서: 시행 → 학년 → 상품 → 총계).
        List<Row> rows = new ArrayList<>();
        Acc dtlSub = null;
        Acc gradeSub = null;
        Acc prodSub = null;
        Acc grand = Acc.empty();
        String curProd = null;
        String curGrade = null;
        Integer curDtl = null;

        for (Acc a : folded.values()) {
            if (dtlSub != null && !Objects.equals(curDtl, a.dtlCd)) {
                rows.add(dtlSub.toRow("DTL_SUBTOTAL", "시행 계"));
                dtlSub = null;
            }
            if (gradeSub != null && !Objects.equals(curGrade, nz(a.grade))) {
                rows.add(gradeSub.toRow("GRADE_SUBTOTAL", "학년 계"));
                gradeSub = null;
            }
            if (prodSub != null && !Objects.equals(curProd, a.productCode)) {
                rows.add(prodSub.toRow("PRODUCT_SUBTOTAL", "상품 계"));
                prodSub = null;
            }
            if (prodSub == null) {
                curProd = a.productCode;
                prodSub = Acc.empty();
            }
            if (gradeSub == null) {
                curGrade = nz(a.grade);
                gradeSub = Acc.empty();
            }
            if (dtlSub == null) {
                curDtl = a.dtlCd;
                dtlSub = Acc.empty();
            }
            rows.add(a.toRow("DETAIL", null));
            dtlSub.merge(a);
            gradeSub.merge(a);
            prodSub.merge(a);
            grand.merge(a);
        }
        if (dtlSub != null) {
            rows.add(dtlSub.toRow("DTL_SUBTOTAL", "시행 계"));
            rows.add(gradeSub.toRow("GRADE_SUBTOTAL", "학년 계"));
            rows.add(prodSub.toRow("PRODUCT_SUBTOTAL", "상품 계"));
        }
        rows.add(grand.toRow("TOTAL", "총 계"));

        return new LogisCostDetailResponse(from, to, mode.name(), g, rows, grand.totalAmount);
    }

    private static String nz(String v) {
        return (v == null) ? "" : v;
    }

    /**
     * 누적 통. 명세행과 소계행이 같은 통을 써서 합산 규칙이 갈리지 않게 한다.
     *
     * <p>⚠️인원은 <b>신청 단위 값</b>이라, 같은 신청이 여러 자재 행으로 쪼개져 오면 중복된다.
     * 게이트웨이가 이미 신청을 GROUP BY에 넣어 한 번만 내주므로 여기서는 그냥 더한다 —
     * 거래처 축으로 접을 때 여러 신청의 인원이 합쳐지는 것은 의도한 동작이다.
     */
    private static final class Acc {
        private LocalDate reqDate;
        private Integer reqCd;
        private String productCode;
        private String productName;
        private String grade;
        private Integer dtlCd;
        private String detailName;
        private String partnerCode;
        private String partnerName;
        private long materialQty;
        private long paperQty;
        private long paperAmount;
        private long omrQty;
        private long omrAmount;
        private long etcQty;
        private long etcAmount;
        private int inwon;
        private long basicAmount;
        private long tradeAmount;
        private long totalAmount;

        private Acc(LogisCostDetailRow r, Grain g) {
            this.reqDate = r.reqDate();
            this.reqCd = (g == Grain.REQUEST) ? r.reqCd() : null;
            this.productCode = r.productCode();
            this.productName = r.productName();
            this.grade = r.grade();
            this.dtlCd = r.dtlCd();
            this.detailName = r.detailName();
            this.partnerCode = r.partnerCode();
            this.partnerName = r.partnerName();
        }

        private Acc() {
        }

        private static Acc empty() {
            return new Acc();
        }

        private void add(LogisCostDetailRow r) {
            materialQty += r.materialQty();
            paperQty += r.paperQty();
            paperAmount += r.paperAmount();
            omrQty += r.omrQty();
            omrAmount += r.omrAmount();
            etcQty += r.etcQty();
            etcAmount += r.etcAmount();
            inwon += r.inwon();
            basicAmount += r.basicAmount();
            tradeAmount += r.tradeAmount();
            totalAmount += r.totalAmount();
            // 접수일자는 가장 이른 것으로 — 거래처 축에서 여러 신청이 합쳐질 때 기준이 필요하다.
            if (reqDate == null || (r.reqDate() != null && r.reqDate().isBefore(reqDate))) {
                reqDate = r.reqDate();
            }
        }

        private void merge(Acc o) {
            materialQty += o.materialQty;
            paperQty += o.paperQty;
            paperAmount += o.paperAmount;
            omrQty += o.omrQty;
            omrAmount += o.omrAmount;
            etcQty += o.etcQty;
            etcAmount += o.etcAmount;
            inwon += o.inwon;
            basicAmount += o.basicAmount;
            tradeAmount += o.tradeAmount;
            totalAmount += o.totalAmount;
        }

        private Row toRow(String rowType, String label) {
            boolean detail = "DETAIL".equals(rowType);
            return new Row(rowType, label,
                    detail ? reqDate : null, detail ? reqCd : null,
                    detail ? productCode : null, detail ? productName : null,
                    detail ? grade : null, detail ? dtlCd : null, detail ? detailName : null,
                    detail ? partnerCode : null, detail ? partnerName : null,
                    materialQty, paperQty, paperAmount, omrQty, omrAmount, etcQty, etcAmount,
                    inwon, basicAmount, tradeAmount, totalAmount);
        }
    }
}
