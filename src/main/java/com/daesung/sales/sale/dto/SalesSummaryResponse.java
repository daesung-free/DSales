package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 순매출 집계 결과. 상품별 행 + 합계행. */
public record SalesSummaryResponse(
        @Schema(description = "집계 시작일") LocalDate fromDate,
        @Schema(description = "집계 종료일") LocalDate toDate,
        @Schema(description = "상품별 집계 행") List<SalesSummaryRow> rows,
        @Schema(description = "합계행") SalesSummaryRow total,
        @Schema(description = """
                **분류별 소계**(발주처 1차회신 2026-10-02 #8 — "분류명에 따른 소계,
                분류코드에 따른 분류 계, 총계"). 묶는 키는 **분류코드**다.

                ★회신은 소계(분류명)와 분류계(분류코드)를 따로 적었지만 우리 마스터에서
                둘은 1:1이라 같은 묶음이 된다 — 따로 내면 같은 숫자가 두 줄로 나온다.
                코드로 묶고 이름을 함께 실으니 화면이 어느 쪽으로 보여주든 한 숫자를 쓴다.

                소계행은 상품 자리가 비어 있고 `productName` 이 `"소계"`다.""")
        List<SalesSummaryRow> categorySubtotals
) {
}
