package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 매출액명세서 응답(조회조건 + rollup 행 목록). 마지막 행이 총계(GRAND_TOTAL). */
public record SalesStatementResponse(
        @Schema(description = "조회 시작일") LocalDate from,
        @Schema(description = "조회 종료일") LocalDate to,
        @Schema(description = "회계구분 필터(null=전체)") String category,
        @Schema(description = "명세 행(상세→소계→분류계→총계 순)") List<SalesStatementRow> rows
) {
}
