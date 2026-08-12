package com.daesung.sales.dashboard.dto;

import com.daesung.sales.dashboard.entity.TargetEntryType;
import com.daesung.sales.dashboard.entity.TargetScope;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 매출목표 등록/수정(upsert).
 *
 * <p>대상 축은 셋이다 — 전사 / 사업부문 / 상품. 발주처가 준 실제 목표가 사업부문 단위여서
 * 그 층이 생겼다(자료요청서 1-6 회신: 더프리미엄·D모의고사·학원 컨텐츠·외부 교재 …).
 */
@Schema(name = "TargetRequest", description = "매출목표(또는 과거연도 실적) 등록·수정")
public record TargetRequest(

        @Schema(description = "연도", example = "2026", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Integer year,

        @Schema(description = "월(1~12). **비우면 연간 목표**", example = "6")
        @Min(1) @Max(12) Integer month,

        @Schema(description = "대상 축 COMPANY(전사)/DIVISION(사업부문)/PRODUCT(상품). 미지정=COMPANY",
                example = "DIVISION")
        TargetScope scope,

        @Schema(description = "사업부문명(scope=DIVISION일 때). 그 외에는 무시된다", example = "더프리미엄")
        String scopeKey,

        @Schema(description = "상품 id(scope=PRODUCT일 때). 그 외에는 무시된다", example = "1")
        Long productId,

        @Schema(description = """
                TARGET(목표) / ACTUAL(확정 실적). 미지정=TARGET.
                ACTUAL은 이관하지 않은 과거연도 실적을 넣어 전년비를 살리는 용도다
                (2025 매출은 우리 DB에 없다).""", example = "TARGET")
        TargetEntryType entryType,

        @Schema(description = "금액", example = "9454430300", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @PositiveOrZero Long targetAmount
) {
    /**
     * 대상 축. 미지정이면 <b>넘어온 값에서 추론</b>한다 —
     * productId가 있으면 상품, scopeKey가 있으면 사업부문, 둘 다 없으면 전사.
     *
     * <p>추론하는 이유: scope는 이번에 새로 생긴 항목이라, 기존처럼 productId만 넘기던 호출이
     * 조용히 전사 목표로 바뀌면 안 된다(상품 목표를 넣었는데 전사에 저장되는 셈이다).
     */
    public TargetScope scopeOrDefault() {
        if (scope != null) {
            return scope;
        }
        if (productId != null) {
            return TargetScope.PRODUCT;
        }
        return (scopeKey != null && !scopeKey.isBlank()) ? TargetScope.DIVISION : TargetScope.COMPANY;
    }

    public TargetEntryType entryTypeOrDefault() {
        return (entryType == null) ? TargetEntryType.TARGET : entryType;
    }
}
