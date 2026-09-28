package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 자재 폐기 결과. 전표번호(P-)와 품목별 처리 후 잔량. */
public record MaterialDisposalResponse(

        @Schema(description = "폐기 전표번호", example = "P-20260608-1") String disposalNo,
        @Schema(description = "창고 id") Long warehouseId,
        @Schema(description = "창고명") String warehouseName,
        @Schema(description = "폐기 품목") List<Line> lines
) {
    @Schema(name = "MaterialDisposalLine")
    public record Line(
            @Schema(description = "자재 id") Long materialId,
            @Schema(description = "자재코드") String materialCode,
            @Schema(description = "자재명") String materialName,
            @Schema(description = "폐기수량(양수)") int qty,
            @Schema(description = "폐기 후 잔량") int currentQty
    ) {
    }
}
