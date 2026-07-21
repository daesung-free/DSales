package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 상품 수정 요청 DTO. 코드(code)는 불변이라 제외. */
public record ProductUpdateRequest(

        @Schema(description = "상품명", example = "2026 D.ARCHIVE 국어 세트", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "콘텐츠구분", example = "SELF", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull ContentType contentType,

        @Schema(description = "세트 여부", example = "true")
        boolean set,

        @Schema(description = "정가(원)", example = "20000")
        Integer price,

        @Schema(description = "면세 여부", example = "false")
        boolean taxFree,

        @Schema(description = "학년", example = "고3")
        String grade,

        @Schema(description = "사용 여부", example = "true")
        boolean useYn
) {
}
