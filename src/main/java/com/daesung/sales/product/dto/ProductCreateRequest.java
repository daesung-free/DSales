package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 상품 등록 요청 DTO. useYn 미지정 시 true. */
public record ProductCreateRequest(

        @Schema(description = "상품코드(고유)", example = "S2026A02", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String code,

        @Schema(description = "상품명", example = "2026 D.ARCHIVE 국어 세트", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "콘텐츠구분(자체교재/외부콘텐츠)", example = "SELF", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull ContentType contentType,

        @Schema(description = "세트(BOM 완제품) 여부", example = "true")
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

        @Schema(description = "사용 여부(미지정 시 true)", example = "true")
        Boolean useYn,

        @Schema(description = "매출구분(매출액정리·순매출조회 집계기준)", example = "정상")
        String salesDivision,

        @Schema(description = "수불부노출 여부(미지정 시 true)", example = "true")
        Boolean ledgerVisible,

        @Schema(description = "Web게시 여부(미지정 시 false)", example = "false")
        Boolean webVisible,

        @Schema(description = "재고관리 여부(미지정 시 true). false=모의고사 등 인원기반, 매출 시 재고 미차감",
                example = "true")
        Boolean stockManaged
) {
    public boolean useYnOrDefault() {
        return useYn == null || useYn;
    }

    public boolean ledgerVisibleOrDefault() {
        return ledgerVisible == null || ledgerVisible;
    }

    public boolean webVisibleOrDefault() {
        return webVisible != null && webVisible;
    }

    public boolean stockManagedOrDefault() {
        return stockManaged == null || stockManaged;
    }
}
