package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 회수단가(DTL_CD=0) 수정 요청. 근거: 레거시 물류비용등록.vb Button_RE_Modify(PAPER/OMR/ETC만). */
public record ReturnRateRequest(
        @Schema(description = "회수 시험지 단가", example = "30") @NotNull @PositiveOrZero Integer paper,
        @Schema(description = "회수 OMR 단가", example = "30") @NotNull @PositiveOrZero Integer omr,
        @Schema(description = "회수 단행본(ETC) 단가", example = "30") @NotNull @PositiveOrZero Integer etc
) {
}
