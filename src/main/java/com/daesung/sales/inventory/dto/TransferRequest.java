package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/** 단순 이고 요청. 출발창고 −, 도착창고 +. 매출 미발생. */
public record TransferRequest(

        @Schema(description = "처리일자", example = "2026-06-08", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = "출발 창고 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long fromWarehouseId,

        @Schema(description = "도착 창고 id", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long toWarehouseId,

        @Schema(description = "이동 품목", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    @Schema(name = "TransferItem")
    public record Item(

            @Schema(description = "상품 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long productId,

            @Schema(description = "이동 수량(양수)", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,

            @Schema(description = "이동 사유", example = "리브커넥스 위탁 물량 이관")
            String reason
    ) {
    }
}
