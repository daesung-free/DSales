package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 콘텐츠구분 순매출. 자체교재(SELF)=매출−반품, 외부콘텐츠(EXTERNAL)=매출−매입=이익.
 * 매입원가는 입고 unit_cost 가중평균. 근거: 레거시 순매출조회 + 콘텐츠구분 축.
 */
public record NetSalesResponse(
        @Schema(description = "집계 시작일") LocalDate fromDate,
        @Schema(description = "집계 종료일") LocalDate toDate,
        @Schema(description = "콘텐츠구분 필터(SELF/EXTERNAL/전체)") String contentType,
        @Schema(description = "상품별 순매출") List<Row> rows,
        @Schema(description = "합계") Row total
) {
    public record Row(
            @Schema(description = "상품 id(합계행 null)") Long productId,
            @Schema(description = "상품코드") String productCode,
            @Schema(description = "상품명") String productName,
            @Schema(description = "콘텐츠구분(SELF/EXTERNAL)") String contentType,
            @Schema(description = "매출수량") long saleQty,
            @Schema(description = "매출액") long saleAmount,
            @Schema(description = "무상액(교사용·증정)") long freeAmount,
            @Schema(description = "반품수량") long returnQty,
            @Schema(description = "반품액") long returnAmount,
            @Schema(description = "순매출수량(매출−반품)") long netQty,
            @Schema(description = "순매출액(매출−반품)") long netAmount,
            @Schema(description = "매입단가(입고원가 평균, 외부콘텐츠만)") Long purchaseUnitCost,
            @Schema(description = "매입액(매입단가×순매출수량, 외부콘텐츠만)") Long purchaseAmount,
            @Schema(description = "이익(순매출액−매입액, 외부콘텐츠만)") Long profit,
            @Schema(description = "이익률 %(이익/순매출액, 외부콘텐츠만)") Double marginPct
    ) {
    }
}
