package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 순매출 집계 결과. 상품별 행 + 합계행. */
public record SalesSummaryResponse(
        @Schema(description = "집계 시작일") LocalDate fromDate,
        @Schema(description = "집계 종료일") LocalDate toDate,
        @Schema(description = "상품별 집계 행") List<SalesSummaryRow> rows,
        @Schema(description = "합계행") SalesSummaryRow total
) {
}
