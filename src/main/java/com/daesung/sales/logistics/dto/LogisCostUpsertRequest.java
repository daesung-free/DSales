package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/** 물류단가 등록/수정 요청(DTL_CD는 경로변수). 근거: 레거시 물류비용등록.vb Button_Mod. */
public record LogisCostUpsertRequest(
        @Schema(description = "시험지 단가", example = "50") @NotNull @PositiveOrZero Integer paper,
        @Schema(description = "OMR 단가", example = "50") @NotNull @PositiveOrZero Integer omr,
        @Schema(description = "단행본(ETC) 단가", example = "50") @NotNull @PositiveOrZero Integer etc,
        @Schema(description = "라벨 단가", example = "0") @NotNull @PositiveOrZero Integer label,
        @Schema(description = "인별 기본작업비", example = "100") @NotNull @PositiveOrZero Integer basic,
        @Schema(description = "인별 배송비", example = "100") @NotNull @PositiveOrZero Integer trade,
        @Schema(description = "포장구분(1~3)", example = "1") @NotNull @Min(1) @Max(3) Integer packtype,
        @Schema(description = "여분포함(Y/N)", example = "Y") @NotNull @Pattern(regexp = "[YN]") String bSpare
) {
}
