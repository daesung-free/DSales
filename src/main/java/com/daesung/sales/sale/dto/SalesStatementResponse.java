package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 매출액명세서 응답(조회조건 + rollup 행 목록). 마지막 행이 총계(GRAND_TOTAL). */
public record SalesStatementResponse(
        @Schema(description = "조회 시작일") LocalDate from,
        @Schema(description = "조회 종료일") LocalDate to,
        @Schema(description = "구분 필터 코드 SALE/RETURN/TEACHER_USE/GIFT(null=전체)") String kind,
        @Schema(description = "구분 필터 명칭(매출/반품/교사용/증정용)") String kindName,
        @Schema(description = "매출유형 필터 NORMAL_SALES/CONSIGN_SALES(null=전체)") String salesType,
        @Schema(description = "명세 행(상세→소계→분류계→총계 순)") List<SalesStatementRow> rows
) {
}
