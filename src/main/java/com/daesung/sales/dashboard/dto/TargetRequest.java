package com.daesung.sales.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 매출목표 등록/수정(upsert). productId 미지정 시 전사 월목표. */
public record TargetRequest(
        @Schema(description = "연도", example = "2026", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Integer year,
        @Schema(description = "월(1~12)", example = "6", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) @Max(12) Integer month,
        @Schema(description = "상품 id(미지정=전사 월목표)", example = "1") Long productId,
        @Schema(description = "목표금액", example = "50000000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @PositiveOrZero Long targetAmount
) {
}
