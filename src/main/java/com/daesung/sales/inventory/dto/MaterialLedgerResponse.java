package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 제품수불부 자재 상세(11p) 응답. 요약(세트·회차)에서 한 행을 골라 들어온 결과다. */
public record MaterialLedgerResponse(

        @Schema(description = "집계 시작일") LocalDate fromDate,
        @Schema(description = "집계 종료일") LocalDate toDate,
        @Schema(description = "세트 상품 id") Long setProductId,
        @Schema(description = "세트 도서코드") String setCode,
        @Schema(description = "세트명") String setName,
        @Schema(description = "선택한 회차 id(미지정이면 세트 전체)") Long roundProductId,
        @Schema(description = "선택한 회차명(미지정이면 null)") String roundName,
        @Schema(description = "세트 자체의 출고수량(소요 기준)") long setConsumedQty,
        @Schema(description = "자재별 소요 현황") List<MaterialLedgerRow> rows
) {
}
