package com.daesung.sales.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/** 거래처별 대분류 공급률 등록/수정 요청(34p). 거래처·대분류는 경로변수. */
public record PartnerSupplyRateRequest(

        @Schema(description = "공급률(%). 단가 = 도서 정가 × 공급률/100", example = "75")
        @PositiveOrZero Integer supplyRate,

        @Schema(description = """
                할인액(원). ⚠️값만 보관한다 — 금액 계산에는 아직 반영되지 않는다.
                레거시 공식 `if(할인액>0, 정가−할인액, 정가×공급률/100)`은 단가 산출식이라
                매출의 금액 단일소스를 함께 고쳐야 반영할 수 있다(별도 작업).""", example = "0")
        @PositiveOrZero Integer discountAmount,

        @Schema(description = "Web게시여부(신청사이트 노출). 미지정 시 기존값 유지, 신규는 true", example = "true")
        Boolean webVisible,

        @Schema(description = "사용여부. 끄면 매출등록 자동조회에서 제외된다", example = "true")
        Boolean useYn
) {
}
