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

        @Schema(description = "분류코드(계층, 첫 글자=대분류)", example = "A01")
        String catCode,

        @Schema(description = "분류명", example = "국어 모의고사")
        String catName,

        @Schema(description = "사용 여부", example = "true")
        boolean useYn,

        @Schema(description = "매출구분(매출액정리·순매출조회 집계기준)", example = "정상")
        String salesDivision,

        @Schema(description = "수불부노출 여부", example = "true")
        boolean ledgerVisible,

        @Schema(description = "Web게시 여부", example = "false")
        boolean webVisible,

        @Schema(description = "재고관리 여부(false=모의고사 등 인원기반, 매출 시 재고 미차감)", example = "true")
        boolean stockManaged
) {
}
