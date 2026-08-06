package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 이고 결과. 품목별 이동 후 출발/도착 잔량. */
public record TransferResponse(
        @Schema(description = "출발 창고 id") Long fromWarehouseId,
        @Schema(description = "출발 창고명") String fromWarehouseName,
        @Schema(description = "도착 창고 id") Long toWarehouseId,
        @Schema(description = "도착 창고명") String toWarehouseName,
        @Schema(description = "이동 결과 품목") List<Line> items
) {
    @Schema(name = "TransferLine")
    public record Line(
            @Schema(description = "상품 id") Long productId,
            @Schema(description = "상품코드") String productCode,
            @Schema(description = "이동 수량") int qty,
            @Schema(description = "이동 후 출발창고 잔량") int fromBalance,
            @Schema(description = "이동 후 도착창고 잔량") int toBalance
    ) {
    }
}
