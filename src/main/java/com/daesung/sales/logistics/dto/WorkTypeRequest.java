package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 작업구분 등록·수정 요청(36p). 수정 시 packType은 무시된다(연결 키라 불변). */
public record WorkTypeRequest(

        @Schema(description = """
                DSRE2 PACKTYPE 값(등록 시 필수, 이후 불변). 단가 행과 잇는 키다 —
                바꾸면 그 작업구분으로 등록된 상품이 통째로 연결을 잃는다.""", example = "4")
        Integer packType,

        @Schema(description = "작업구분명", example = "개별봉투(대형)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 50) String name,

        @Schema(description = "시험지 기준단가", example = "40") @PositiveOrZero int paper,
        @Schema(description = "OMR 기준단가", example = "40") @PositiveOrZero int omr,
        @Schema(description = "단행본 기준단가", example = "40") @PositiveOrZero int etc,
        @Schema(description = "라벨 기준단가", example = "100") @PositiveOrZero int label,
        @Schema(description = "기본작업비 기준단가", example = "100") @PositiveOrZero int basic,
        @Schema(description = "출고비 기준단가", example = "100") @PositiveOrZero int trade,

        @Schema(description = "사용여부(미지정 시 수정에서는 기존값 유지)") Boolean useYn,
        @Schema(description = "정렬 순서") Integer sortOrder
) {
}
