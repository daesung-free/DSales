package com.daesung.sales.consignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/** 위탁출고 등록. 물류창고 → 위탁창고 이고 + 미결원장(consignment_out) 생성. 매출 미발생. */
public record ConsignmentOutRequest(

        @Schema(description = "처리일자", example = "2026-06-08", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = "위탁 거래처 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long partnerId,

        @Schema(description = "출발(물류) 창고 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long fromWarehouseId,

        @Schema(description = "도착(위탁) 창고 id", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long toWarehouseId,

        @Schema(description = "위탁출고 품목", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    public record Item(

            @Schema(description = "상품 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long productId,

            @Schema(description = "출고 수량(양수)", example = "1000", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty
    ) {
    }
}
