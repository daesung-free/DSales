package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 자재 입고 결과. 전표번호(IN-)와 품목별 처리 후 잔량. */
public record MaterialInboundResponse(

        @Schema(description = "입고 전표번호", example = "IN-20260608-1") String inboundNo,
        @Schema(description = "도착 창고 id") Long warehouseId,
        @Schema(description = "도착 창고명") String warehouseName,
        @Schema(description = "입고 품목") List<Line> lines
) {
    @Schema(name = "MaterialInboundLine")
    public record Line(
            @Schema(description = "자재 id") Long materialId,
            @Schema(description = "자재코드") String materialCode,
            @Schema(description = "자재명") String materialName,
            @Schema(description = "입고수량") int qty,
            @Schema(description = "입고 후 잔량") int currentQty
    ) {
    }
}
