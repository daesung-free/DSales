package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 재고실사 결과. 상품별 시스템수량↔실물수량 대조 + 조정 결과. */
public record StocktakeResponse(
        @Schema(description = "실사 id") Long id,
        @Schema(description = "실사번호", example = "ST-20260630-1") String stocktakeNo,
        @Schema(description = "창고 id") Long warehouseId,
        @Schema(description = "창고명") String warehouseName,
        @Schema(description = "실사일자") LocalDate stocktakeDate,
        @Schema(description = "차이가 발생해 조정된 품목 수") int adjustedCount,
        @Schema(description = "실사 명세") List<Line> lines,
        @Schema(description = """
                경고 — **막지 않고 알리기만 한다**. 비어 있으면 이상 없음.
                `PERIOD_LOCKED` 는 마감된 달에 등록한 경우다(발주처 2026-10-23: 차단 안 함, 안내만).
                ‼️전표 단위 경고라 상품·창고 칸이 비어 있다 — 코드로 갈라 읽을 것.""")
        java.util.List<StockWarning> warnings
) {
    @Schema(name = "StocktakeLine")
    public record Line(
            @Schema(description = "상품 id") Long productId,
            @Schema(description = "상품코드") String productCode,
            @Schema(description = "상품명") String productName,
            @Schema(description = "시스템수량(대조 시점 캐시)") int systemQty,
            @Schema(description = "실물수량") int countedQty,
            @Schema(description = "차이(실물−시스템, 조정량)") int diff,
            @Schema(description = "조정 후 잔량(=실물수량)") int newBalance
    ) {
    }
}
