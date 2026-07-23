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
            @Schema(description = "반품률(%, 반품÷매출×100, 매출0이면 null)") Double returnRate
    ) {
    }
}
