package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 세트 조립 작업비 1행. 물류팀이 다운로드해 가공·정산 요청하는 내역.
 * 근거: 발주처 확정 2026-08-05 — "자동 계산된 내역을 다운로드하여 가공 후 작업비 정산 요청".
 */
public record AssemblyCostRow(
        @Schema(description = "작업일자") LocalDate workDate,
        @Schema(description = "창고명") String warehouseName,
        @Schema(description = "세트 도서코드") String productCode,
        @Schema(description = "세트명") String productName,
        @Schema(description = "조립 수량") long workQty,
        @Schema(description = "조립 작업비(자동계산)") long workCost,
        @Schema(description = "비고") String memo
) {
}
