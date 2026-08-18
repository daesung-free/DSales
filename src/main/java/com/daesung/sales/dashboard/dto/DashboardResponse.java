package com.daesung.sales.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 매출 대시보드: 목표 대비 실적(월별 + 연간 합계). 근거: 대시보드 화면(19·20p). */
public record DashboardResponse(
        @Schema(description = "연도") int year,
        @Schema(description = "대상 축") com.daesung.sales.dashboard.entity.TargetScope scope,
        @Schema(description = "사업부문명(scope=DIVISION일 때)") String scopeKey,
        @Schema(description = "상품 id(scope=PRODUCT일 때)") Long productId,
        @Schema(description = "실적 기준시각 — 새벽 배치가 계산한 시각. null이면 조회 시점 실시간 계산")
        java.time.LocalDateTime computedAt,
        @Schema(description = "월별 목표/실적") List<MonthCell> months,
        @Schema(description = "연간 합계") YearSummary summary
) {
    public record MonthCell(
            @Schema(description = "월(1~12)") int month,
            @Schema(description = "당월 목표금액") long target,
            @Schema(description = "누적 목표금액(1월~해당월 합)") long cumulativeTarget,
            @Schema(description = "당월 실적(순매출)") long actual,
            @Schema(description = "누적 실적(1월~해당월 합)") long cumulativeActual,
            @Schema(description = "달성률 %(실적/목표), 목표 0이면 null") Double achievementPct,
            @Schema(description = "전년 동월 실적") long prevActual,
            @Schema(description = "전년比 성장률 %((실적−전년)/전년), 전년 0이면 null") Double growthPct
    ) {
    }

    public record YearSummary(
            @Schema(description = "연간 목표(연간 목표가 등록돼 있으면 그 값, 없으면 월 목표 합)") long totalTarget,
            @Schema(description = "연간 실적 합") long totalActual,
            @Schema(description = "연간 달성률 %") Double achievementPct,
            @Schema(description = "전년 연간 실적(그 해 매출이 없으면 저장된 확정 실적)") long prevTotalActual,
            @Schema(description = "전년比 성장률 %") Double growthPct
    ) {
    }
}
