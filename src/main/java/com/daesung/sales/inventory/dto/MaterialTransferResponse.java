package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 자재 이고 결과. 전표번호(TR-)와 품목별 출발/도착 잔량. */
public record MaterialTransferResponse(

        @Schema(description = "이고 전표번호", example = "TR-20260608-1") String transferNo,
        @Schema(description = "출발 창고 id") Long fromWarehouseId,
        @Schema(description = "출발 창고명") String fromWarehouseName,
        @Schema(description = "도착 창고 id") Long toWarehouseId,
        @Schema(description = "도착 창고명") String toWarehouseName,
        @Schema(description = "이동 품목") List<Line> lines
) {
    @Schema(name = "MaterialTransferLine")
    public record Line(
            @Schema(description = "자재 id") Long materialId,
            @Schema(description = "자재코드") String materialCode,
            @Schema(description = "자재명") String materialName,
            @Schema(description = "이동수량") int qty,
            @Schema(description = "출발창고 잔량") int fromQty,
            @Schema(description = "도착창고 잔량") int toQty
    ) {
    }
}
