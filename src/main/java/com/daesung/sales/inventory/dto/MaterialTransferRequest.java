package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/**
 * 자재 이고(창고 이동) 요청.
 *
 * <p>도서 이고({@link TransferRequest})와 같은 모양이다 — {@code productId} 자리에 {@code materialId}.
 */
public record MaterialTransferRequest(

        @Schema(description = "처리일자", example = "2026-06-08", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = "출발 창고 id", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long fromWarehouseId,

        @Schema(description = "도착 창고 id", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long toWarehouseId,

        @Schema(description = "이동 자재 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    @Schema(name = "MaterialTransferItem")
    public record Item(
            @Schema(description = "자재 id", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long materialId,
            @Schema(description = "이동수량(양수)", example = "500", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,
            @Schema(description = "비고", example = "물류창고 재배치") String memo
    ) {
    }
}
