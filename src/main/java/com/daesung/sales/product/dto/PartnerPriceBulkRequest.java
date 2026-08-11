package com.daesung.sales.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * 거래처별 단가 <b>일괄 적용</b> 요청. 근거: 발주처 확정 "거래처별 단가·물류비용등록 일괄 적용".
 *
 * <p>왜 필요한가: 발주처는 공급률을 <b>거래처구분별 대표값</b>으로 운영한다(자료요청서 1-2 —
 * "특약점 일괄 70, B2B 일괄 85"). 한 건씩 넣으면 도서 하나에 거래처 수십 번을 눌러야 한다.
 */
@Schema(name = "PartnerPriceBulkRequest", description = "거래처별 단가 일괄 적용")
public record PartnerPriceBulkRequest(

        @Schema(description = "적용할 거래처 id 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty List<Long> partnerIds,

        @Schema(description = "공급률(%). 목록의 전 거래처에 같은 값으로 적용", example = "70",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @PositiveOrZero Integer supplyRate,

        @Schema(description = "노출 여부(미지정 시 true)", example = "true")
        Boolean visible,

        @Schema(description = """
                이미 매핑이 있는 거래처를 덮어쓸지. 기본 false —
                예외 단가를 따로 넣어둔 거래처가 일괄 적용에 조용히 지워지면 안 된다.""",
                example = "false")
        Boolean overwrite
) {
    public boolean visibleOrDefault() {
        return visible == null || visible;
    }

    public boolean overwriteOrDefault() {
        return overwrite != null && overwrite;
    }
}
