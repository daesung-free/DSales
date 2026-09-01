package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 폐기 <b>분류명별 요약</b>(10p). 근거: 발주처 화면검토(2026-08-31) —
 * "폐기 내역 조회시에도 분류명 별로 해당 내역 요약(전체)/상세가 모두 조회 가능한지".
 *
 * <p>낱건은 {@code GET /disposals}(상세)가 준다. 여기는 분류 단위 합계다 —
 * "이번 달 어느 분류에서 얼마나 버렸나"를 한눈에 보는 용도.
 */
public record DisposalSummaryResponse(

        @Schema(description = "집계 시작일") LocalDate fromDate,
        @Schema(description = "집계 종료일") LocalDate toDate,
        @Schema(description = "분류별 요약") List<Row> rows,
        @Schema(description = "총 폐기건수") long totalCount,
        @Schema(description = "총 폐기수량") long totalQty
) {
    @Schema(name = "DisposalSummaryRow")
    public record Row(
            @Schema(description = "분류코드") String catCode,
            @Schema(description = "분류명") String catName,
            @Schema(description = "폐기건수(전표 라인 수)") long count,
            @Schema(description = "폐기수량(양수 — 원장은 음수로 기록된다)") long qty
    ) {
    }
}
