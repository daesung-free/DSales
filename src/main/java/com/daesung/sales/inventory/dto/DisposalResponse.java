package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 폐기 결과. 폐기번호 + 품목별 폐기수량·잔량. */
public record DisposalResponse(
        @Schema(description = "폐기번호(P)", example = "P-20260626-1") String disposalNo,
        @Schema(description = "창고 id") Long warehouseId,
        @Schema(description = "창고명") String warehouseName,
        @Schema(description = "폐기 결과 품목") List<Line> items,
        @Schema(description = "재고 경고(차단 아님). 음수가 되면 담긴다 — 발주처 2026-08-31로 차단은 하지 않는다") java.util.List<StockWarning> warnings
) {
    @Schema(name = "DisposalLine")
    public record Line(
            @Schema(description = "상품 id") Long productId,
            @Schema(description = "상품코드") String productCode,
            @Schema(description = "폐기 수량") int qty,
            @Schema(description = "폐기 후 잔량") int balance
    ) {
    }
}
