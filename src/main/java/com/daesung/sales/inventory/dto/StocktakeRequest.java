package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.List;

/** 재고실사 등록. 상품별 실물수량을 입력하면 시스템(캐시)과 대조해 차이를 조정한다. */
public record StocktakeRequest(

        @Schema(description = "실사 창고 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long warehouseId,

        @Schema(description = "실사일자", example = "2026-06-30", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate stocktakeDate,

        @Schema(description = "메모", example = "6월 정기 재고실사")
        String memo,

        @Schema(description = "실사 품목", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    @Schema(name = "StocktakeItem")
    public record Item(

            @Schema(description = "상품 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long productId,

            @Schema(description = "실물 수량(0 이상)", example = "95", requiredMode = Schema.RequiredMode.REQUIRED)
            @PositiveOrZero int countedQty
    ) {
    }
}
