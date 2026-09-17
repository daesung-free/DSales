package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 순매출 집계 한 행(상품별, 취소 제외). 매출/증정/교사용/반품 버킷 + 순매출.
 * 순매출수량 = 매출수량 − 반품수량, 순매출액 = 매출액 − 반품액. 근거: 레거시 순매출조회.
 */
public record SalesSummaryRow(
        @Schema(description = "상품 id(합계행은 null)") Long productId,
        @Schema(description = "상품코드(합계행은 '합계')") String productCode,
        @Schema(description = "상품명") String productName,

        @Schema(description = """
                구분 — 매출/반품/교사용/증정용. **조회 시 구분을 지정했을 때만** 값이 있다.
                지정하지 않으면 한 행에 네 구분이 다 들어 있어(saleQty·returnQty·…) 한 값으로 못 적는다.""")
        String summaryKind,

        @Schema(description = """
                매출유형 — 일반매출/위탁매출. **조회 시 매출유형을 지정했을 때만** 값이 있다.""")
        String salesType,
        @Schema(description = "매출수량") long saleQty,
        @Schema(description = "매출액(공급가)") long saleAmount,
        @Schema(description = "증정수량") long freeQty,
        @Schema(description = "증정액") long freeAmount,
        @Schema(description = "교사용수량") long teacherQty,
        @Schema(description = "교사용액") long teacherAmount,
        @Schema(description = "반품수량") long returnQty,
        @Schema(description = "반품액") long returnAmount,
        @Schema(description = "순매출수량(매출−반품)") long netQty,
        @Schema(description = "순매출액(매출−반품)") long netAmount,
        @Schema(description = "세액합") long tax,
        @Schema(description = "합계금액(공급가+세, 전 구분)") long total
) {
}
