package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.MajorCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * 거래처별 공급률 <b>일괄 적용</b>(34p). 정본 원문:
 * "거래처구분 필터로 좁힌 뒤 여러 거래처를 체크해 '선택 거래처 일괄 적용'으로
 * 공급률·Web게시여부·할인액을 일괄 반영".
 *
 * <p>발주처는 공급률을 거래처구분별 대표값으로 운영한다(특약점 일괄 70, B2B 일괄 85).
 * 한 건씩 넣으면 대분류 하나에 거래처 수십 번을 눌러야 한다.
 */
@Schema(name = "PartnerSupplyRateBulkRequest", description = "거래처별 공급률 일괄 적용")
public record PartnerSupplyRateBulkRequest(

        @Schema(description = "적용할 거래처 id 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty List<Long> partnerIds,

        @Schema(description = "적용할 대분류", example = "ETC_EXAM", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull MajorCategory majorCategory,

        @Schema(description = "공급률(%). 목록의 전 거래처에 같은 값으로", example = "75")
        @PositiveOrZero Integer supplyRate,

        @Schema(description = "할인액(원)", example = "0")
        @PositiveOrZero Integer discountAmount,

        @Schema(description = "Web게시여부", example = "true") Boolean webVisible,

        @Schema(description = "사용여부", example = "true") Boolean useYn,

        @Schema(description = """
                이미 매핑이 있는 거래처를 덮어쓸지. 기본 false —
                예외 단가를 따로 넣어둔 거래처가 일괄 적용에 조용히 덮이면 잘못된 금액으로 매출이 등록된다.""",
                example = "false")
        Boolean overwrite
) {
    public boolean overwriteOrDefault() {
        return overwrite != null && overwrite;
    }
}
