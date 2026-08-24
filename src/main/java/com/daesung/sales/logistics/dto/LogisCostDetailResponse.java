package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 물류 작업비 명세(28p) — 행 + 소계. 근거: 레거시 물류비계산2.vb 그리드
 * ({@code GROUP BY … WITH ROLLUP}) + 정본 28p 주요 데이터 항목.
 *
 * <p>★레거시는 화면 체크박스({@code CheckBox_ViewDetail})로 <b>묶는 축이 갈린다</b>:
 * <pre>
 *   상세 ON  : 상품 · 학년 · 시행 · <b>신청</b>
 *   상세 OFF : 상품 · 학년 · 시행 · <b>거래처</b>
 * </pre>
 * 우리는 가장 잘게(신청·거래처 둘 다) 한 번 받아 두고 여기서 접는다 —
 * 모드마다 쿼리를 따로 두면 물류비 계산식이 두 벌로 갈라진다.
 *
 * <p>소계는 레거시 ROLLUP 그대로 <b>시행 → 학년 → 상품 → 총계</b> 순으로 붙는다.
 */
@Schema(name = "LogisCostDetailResponse", description = "물류 작업비 명세(28p)")
public record LogisCostDetailResponse(
        @Schema(description = "조회 시작일") LocalDate fromDate,
        @Schema(description = "조회 종료일") LocalDate toDate,
        @Schema(description = "구분 ALL/NORMAL/ACCIDENT") String mode,
        @Schema(description = "묶는 축 REQUEST(신청)/PARTNER(거래처)") Grain grain,
        @Schema(description = """
                **확정분인가**. true면 마감 때 굳혀 둔 값이라 단가를 바꿔도 안 변한다.
                false면 조회 시점에 다시 계산한 값이다(마감 전이거나, 마감했지만
                DSRE 연동이 꺼져 있어 굳히지 못한 달).
                기간이 여러 달에 걸치면 **전부 굳어 있을 때만** true다.""")
        boolean confirmed,
        @Schema(description = "명세 + 소계") List<Row> rows,
        @Schema(description = "총 금액합계") long totalAmount
) {
    /** 마지막 묶음 축. 레거시 '상세보기' 체크박스에 대응한다. */
    @Schema(name = "LogisCostGrain")
    public enum Grain {
        /** 상세 ON — 신청번호까지 펼친다. */
        REQUEST,
        /** 상세 OFF — 거래처로 묶는다(한 거래처의 여러 신청이 한 줄). */
        PARTNER
    }

    @Schema(name = "LogisCostDetailRowView")
    public record Row(
            @Schema(description = "행 종류", allowableValues = {"DETAIL", "DTL_SUBTOTAL",
                    "GRADE_SUBTOTAL", "PRODUCT_SUBTOTAL", "TOTAL"})
            String rowType,
            @Schema(description = "소계 표시명. 명세행은 null") String label,
            @Schema(description = "접수일자(명세행)") LocalDate reqDate,
            @Schema(description = "신청번호(grain=REQUEST일 때)") Integer reqCd,
            @Schema(description = "상품코드") String productCode,
            @Schema(description = "상품명") String productName,
            @Schema(description = "학년") String grade,
            @Schema(description = "시행코드") Integer dtlCd,
            @Schema(description = "시행명") String detailName,
            @Schema(description = "거래처코드") String partnerCode,
            @Schema(description = "거래처명") String partnerName,
            @Schema(description = "자재 합") long materialQty,
            @Schema(description = "시험지 합") long paperQty,
            @Schema(description = "시험지 금액") long paperAmount,
            @Schema(description = "OMR 합") long omrQty,
            @Schema(description = "OMR 금액") long omrAmount,
            @Schema(description = "기타 합") long etcQty,
            @Schema(description = "기타 금액") long etcAmount,
            @Schema(description = "인원") int inwon,
            @Schema(description = "기본작업비") long basicAmount,
            @Schema(description = "출고비") long tradeAmount,
            @Schema(description = "금액합계") long totalAmount
    ) {
    }
}
