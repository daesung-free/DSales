package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.List;

/** BOM 구성 등록(완제품=path의 상품, 구성품 목록). 기존 구성은 대체됨. */
public record BomRegisterRequest(

        @Schema(description = "구성품 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Component> components
) {
    /**
     * 세트 구성 1행(33p 세트구성 상세 탭).
     * 회차별 자재 단위로 등록하므로 같은 자재가 회차만 달리해 반복될 수 있다.
     */
    @Schema(name = "BomRegisterComponent")
    public record Component(

            @Schema(description = "구성품(자재) 상품 id", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long childProductId,

            @Schema(description = "세트당 소요수량(BOM 비율, 완제품 1개당 구성품 수)",
                    example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int ratio,

            @Schema(description = "구성회차(미지정·0 = 회차 구분 없음)", example = "1")
            @PositiveOrZero Integer round,

            @Schema(description = "시행예정일(회차별)", example = "2026-09-01")
            LocalDate examDate,

            @Schema(description = "분리포장여부(미지정 시 false)", example = "false")
            Boolean separatePack,

            @Schema(description = "자재구분 — 물류비용등록 작업구분과 1:1 대응. "
                    + "EXAM_PAPER(시험지)/ANSWER_SHEET(해설지)/OMR/LABEL(라벨)/ETC",
                    example = "EXAM_PAPER")
            MaterialType materialType,

            @Schema(description = "물류비용 연계 — 물류비용등록(36p) 작업구분(PACKTYPE). 3=개별봉투(SET)",
                    example = "3")
            Integer packType
    ) {
    }
}
