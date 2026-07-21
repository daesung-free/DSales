package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/** 폐기 등록 요청. 등록 수량만큼 재고 즉시 차감(음수재고 방지). */
public record DisposalRequest(

        @Schema(description = "처리일자", example = "2026-06-26", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = "창고 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long warehouseId,

        @Schema(description = "폐기 품목", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    public record Item(

            @Schema(description = "상품 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long productId,

            @Schema(description = "폐기 수량(양수)", example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,

            @Schema(description = "폐기 사유", example = "파본")
            String reason
    ) {
    }
}
