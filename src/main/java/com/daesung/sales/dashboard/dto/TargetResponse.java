package com.daesung.sales.dashboard.dto;

import com.daesung.sales.dashboard.entity.SalesTarget;
import com.daesung.sales.dashboard.entity.TargetEntryType;
import com.daesung.sales.dashboard.entity.TargetScope;
import io.swagger.v3.oas.annotations.media.Schema;

/** 등록된 매출목표(또는 과거연도 확정 실적) 1건. */
@Schema(name = "TargetResponse", description = "매출목표·실적 항목")
public record TargetResponse(
        @Schema(description = "id") Long id,
        @Schema(description = "연도") int year,
        @Schema(description = "월(null이면 연간)") Integer month,
        @Schema(description = "대상 축") TargetScope scope,
        @Schema(description = "사업부문명(scope=DIVISION)") String scopeKey,
        @Schema(description = "상품 id(scope=PRODUCT)") Long productId,
        @Schema(description = "TARGET(목표)/ACTUAL(확정 실적)") TargetEntryType entryType,
        @Schema(description = "금액") long amount
) {
    public static TargetResponse from(SalesTarget t) {
        return new TargetResponse(t.getId(), t.getFiscalYear(), t.getMonth(), t.getScope(),
                t.getScopeKey(), t.getProductId(), t.getEntryType(), t.getTargetAmount());
    }
}
