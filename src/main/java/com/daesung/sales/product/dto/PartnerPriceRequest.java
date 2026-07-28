package com.daesung.sales.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/** 거래처별 단가·노출 매핑 등록/수정 요청. 도서·거래처는 경로변수. */
public record PartnerPriceRequest(

        @Schema(description = "거래처별 공급률(%). 단가=정가×공급률/100", example = "70")
        @PositiveOrZero Integer supplyRate,

        @Schema(description = "거래처별 노출 여부(미지정 시 true)", example = "true")
        Boolean visible
) {
    public boolean visibleOrDefault() {
        return visible == null || visible;
    }
}
