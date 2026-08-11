package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
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

        @Schema(description = "분류코드 — [영문1자][연도4자][영문·숫자1~3자]. 첫 글자=대분류, 연도가 코드에 포함된다",
                example = "M2026A01")
        @CatCode String catCode,

        @Schema(description = "분류명", example = "국어 모의고사")
        String catName,

        @Schema(description = "사용 여부(미지정 시 true)", example = "true")
        Boolean useYn,

        @Schema(description = "매출구분(매출액정리·순매출조회 집계기준)", example = "정상")
        String salesDivision,

        @Schema(description = "상품년도(32p)", example = "2026")
        Integer productYear,

        @Schema(description = "상품구분(32p, 레거시 bookData.type 원시값)", example = "교재")
        String productType,

        @Schema(description = "기본 공급률(%). 거래처별 매핑이 없을 때 적용되는 바탕값", example = "75")
        @PositiveOrZero @Max(100) Integer supplyRate,

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
