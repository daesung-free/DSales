package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 과목별매출현황 응답(거래처×분류×도서 수량·반품률). 근거: 레거시 과목별매출현황.vb. */
public record CategorySalesResponse(
        @Schema(description = "조회 시작일") LocalDate from,
        @Schema(description = "조회 종료일") LocalDate to,
        @Schema(description = "거래처 필터(null=전체)") Long partnerId,
        @Schema(description = "분류코드 필터(null=전체)") String catCode,
        @Schema(description = "행 목록(거래처→분류→도서 순)") List<Row> rows
) {
    /** 과목별매출현황 한 행(거래처×분류×도서). */
    @Schema(name = "CategorySalesRow")
    public record Row(
            @Schema(description = "거래처 id") Long partnerId,
            @Schema(description = "거래처코드") String partnerCode,
            @Schema(description = "거래처명") String partnerName,
            @Schema(description = "분류코드") String catCode,
            @Schema(description = "분류명") String catName,
            @Schema(description = "도서코드") String bookCode,
            @Schema(description = "도서명") String bookName,
            @Schema(description = "매출수량") long saleQty,
            @Schema(description = "반품수량") long returnQty,
            @Schema(description = "순매출수량(매출−반품)") long netQty,
            @Schema(description = "교사용수량") long teacherQty,
            @Schema(description = "반품률(%, 반품÷매출×100, 매출0이면 null)") Double returnRate,

            @Schema(description = """
                    매출금액(공급가). 레거시 과목별매출현황은 **수량만** 보여 줬지만,
                    같은 축을 거래처로 rollup 해 쓰는 **처리내역서**(레거시 `처리내역서.vb`,
                    인쇄물 제목은 「거래처별 순매출 조회」)가 금액을 요구한다 — 그쪽은
                    `sum(매출수량)·sum(매출금액)` 두 칸이 본문이다.""")
            long saleAmount,
            @Schema(description = "반품금액(공급가)") long returnAmount,
            @Schema(description = "순매출금액(매출−반품)") long netAmount,
            @Schema(description = """
                    교사용(무가)금액. ‼️레거시 처리내역서는 이 값을 **뽑아 놓고 쓰지 않았다**
                    (서브쿼리엔 있는데 바깥 집계에 없다). 원래 보여주려던 항목으로 보여 함께 낸다 —
                    화면에 쓸지는 그쪽에서 정하면 된다.""")
            long teacherAmount
    ) {
    }
}
