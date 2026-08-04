package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회차별 작업현황 1행(구 IC회차별작업현황).
 * 근거: 레거시 IC회차별작업현황.vb — 분류×도서×회차를 행으로, 포장구분 3종을 열로 펼친다.
 */
public record RoundWorkStatusRow(
        @Schema(description = "분류코드") String catCode,
        @Schema(description = "분류명") String catName,
        @Schema(description = "도서코드") String productCode,
        @Schema(description = "도서명") String productName,
        @Schema(description = "회차") Integer bookRound,
        @Schema(description = "개별1(개별봉투) 수량") long individual1,
        @Schema(description = "개별2(개별봉투 SET) 수량") long individual2,
        @Schema(description = "반별(반별봉투) 수량") long classBundle,
        @Schema(description = "합계 수량") long total
) {
}
