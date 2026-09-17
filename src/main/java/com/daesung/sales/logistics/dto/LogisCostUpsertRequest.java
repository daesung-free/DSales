package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
        @Schema(description = """
                작업구분(PACKTYPE). **등록된 작업구분이어야 한다** — 값 범위를 코드에 박지 않고
                작업구분 관리(`/masters/work-types`)를 단일 기준으로 삼는다.
                모르는 값이면 400과 함께 사용 가능한 목록을 돌려준다.""", example = "1")
        @NotNull @Positive Integer packtype,
        @Schema(description = "여분포함(Y/N)", example = "Y") @NotNull @Pattern(regexp = "[YN]") String bSpare
) {
}
