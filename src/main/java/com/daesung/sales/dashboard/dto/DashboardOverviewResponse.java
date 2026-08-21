package com.daesung.sales.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 기본 통계 대시보드(19p). 근거: 정본 19p 핵심 요구사항 —
 * "당월/누적 순매출액, 더프리미엄 누적매출(점유율1위), 특약점 당월매출 등 KPI 카드 +
 * 제품별 목표대비 차트 + 월별누적트렌드 + 거래처별비중 도넛 + 순매출TOP5".
 *
 * <p>★<b>20p와 원천이 같다</b>. 정본이 "20페이지 매출상세대시보드와 원천데이터 공유 확인"을
 * 조건으로 달았으므로, 순매출 식({@code SALE 공급가 − RETURN 공급가}, 취소 제외)을 20p와
 * 한 곳에서 공유한다. 식이 갈리면 같은 달인데 두 화면 숫자가 다르게 나온다.
 */
@Schema(name = "DashboardOverviewResponse", description = "기본 통계 대시보드(19p)")
public record DashboardOverviewResponse(
        @Schema(description = "기준 연도") int year,
        @Schema(description = "기준 월") int month,
        @Schema(description = "KPI 카드") Kpi kpi,
        @Schema(description = "제품별 목표대비(당해 누적, 목표 등록분만)") List<ProductTarget> productTargets,
        @Schema(description = "월별 누적 트렌드(1~12월)") List<TrendPoint> monthlyTrend,
        @Schema(description = "거래처별 비중(당해 누적, 비중 큰 순)") List<Share> partnerShares,
        @Schema(description = "순매출 TOP5(당해 누적)") List<Share> topProducts
) {
    @Schema(name = "DashboardKpi")
    public record Kpi(
            @Schema(description = "당월 순매출액") long monthNetSales,
            @Schema(description = "누적 순매출액(1월~기준월)") long cumulativeNetSales,
            @Schema(description = """
                    점유율 1위 상품군(대분류)의 누적 순매출. 정본 예시는 '더프리미엄'이지만
                    특정 이름을 코드에 박지 않는다 — **점유율 1위를 계산해서** 낸다.
                    이름을 박으면 그 상품군이 1위가 아니게 된 순간 카드가 거짓말을 한다.""")
            long topCategoryNetSales,
            @Schema(description = "점유율 1위 상품군 명칭(모의고사·교재 …). 매출이 없으면 null")
            String topCategoryName,
            @Schema(description = "점유율 1위 상품군의 비중 %") Double topCategorySharePct,
            @Schema(description = "특약점 당월 순매출(거래처구분='특약점')") long dealerMonthNetSales
    ) {
    }

    @Schema(name = "DashboardProductTarget")
    public record ProductTarget(
            @Schema(description = "상품 id") Long productId,
            @Schema(description = "상품명") String productName,
            @Schema(description = "목표(연간 목표가 있으면 그 값, 없으면 월 목표 합)") long target,
            @Schema(description = "누적 실적(순매출)") long actual,
            @Schema(description = "달성률 %. 목표 0이면 null") Double achievementPct
    ) {
    }

    @Schema(name = "DashboardTrendPoint")
    public record TrendPoint(
            @Schema(description = "월(1~12)") int month,
            @Schema(description = "당월 순매출") long netSales,
            @Schema(description = "누적 순매출") long cumulative
    ) {
    }

    @Schema(name = "DashboardShare")
    public record Share(
            @Schema(description = "키(거래처 id 또는 상품 id)") String key,
            @Schema(description = "표시명") String name,
            @Schema(description = "순매출") long netSales,
            @Schema(description = "비중 %(전체 대비). 전체가 0이면 null") Double sharePct
    ) {
    }
}
