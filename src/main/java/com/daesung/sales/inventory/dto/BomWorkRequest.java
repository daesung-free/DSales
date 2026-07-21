package com.daesung.sales.inventory.dto;

import com.daesung.sales.inventory.entity.BomDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

/** BOM 조립/해체 요청. 구성품·비율은 상품 BOM 마스터(bom_items)에서 읽는다. */
public record BomWorkRequest(

        @Schema(description = "처리일자", example = "2026-06-08", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = "작업 창고 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long warehouseId,

        @Schema(description = "방향(ASSEMBLE 조립 / DISASSEMBLE 해체)", example = "ASSEMBLE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull BomDirection direction,

        @Schema(description = "완제품(세트) 상품 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long parentProductId,

        @Schema(description = "작업 수량(완제품 기준, 양수)", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
        @Positive int workQty,

        @Schema(description = "비고", example = "포장 작업")
        String memo
) {
}
