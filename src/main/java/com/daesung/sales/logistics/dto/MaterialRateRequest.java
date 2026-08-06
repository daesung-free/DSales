package com.daesung.sales.logistics.dto;

import com.daesung.sales.product.entity.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 자재 단가 등록·수정. 세트 조립 작업비 자동계산의 단가 소스. */
public record MaterialRateRequest(

        @Schema(description = "자재구분 EXAM_PAPER(시험지)/ANSWER_SHEET(해설지)/OMR/LABEL(라벨)/ETC",
                example = "EXAM_PAPER", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull MaterialType materialType,

        @Schema(description = "작업구분(물류비용등록 PACKTYPE). 미지정·0이면 공통 단가", example = "3")
        @PositiveOrZero Integer packType,

        @Schema(description = "자재 1개당 단가(원)", example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @PositiveOrZero Integer unitRate,

        @Schema(description = "비고") String memo
) {
}
