package com.daesung.sales.closing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 월마감/해제 요청. */
public record PeriodLockRequest(

        @Schema(description = "대상 연도", example = "2026", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Integer year,

        @Schema(description = "대상 월(1~12)", example = "6", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) @Max(12) Integer month,

        @Schema(description = "메모(마감 사유 등)", example = "6월 정기 마감")
        String memo
) {
}
