package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 물류단가 신규등록(36p). <b>단가를 받지 않는다</b> —
 * 작업구분을 고르면 그 기준단가가 자동으로 적용된다(발주처 §1-10 원문).
 */
@Schema(name = "ProductLogisRateRequest", description = "매출프로그램 상품 물류단가 신규등록")
public record ProductLogisRateRequest(

        @Schema(description = "매출프로그램 상품 id", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long productId,

        @Schema(description = """
                작업구분(PACKTYPE). **등록된 작업구분이어야 한다** —
                고르는 순간 그 기준단가(시험지·OMR·단행본·라벨·기본작업비·출고비)가 그대로 복사된다.""",
                example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Positive Integer packType,

        @Schema(description = "여분포함(Y/N). 미지정 시 Y", example = "Y")
        @Pattern(regexp = "[YN]") String bSpare
) {
}
