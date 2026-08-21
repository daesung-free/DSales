package com.daesung.sales.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 기본 통계 대시보드(19p) 회귀 고정.
 *
 * <p>★가장 중요한 것은 <b>20p와 숫자가 같아야 한다</b>는 점이다. 정본 19p가
 * "20페이지 매출상세대시보드와 원천데이터 공유 확인(5월 실적 2,500=25.0억 일치)"을
 * 조건으로 달아 두었다. 순매출 식이 갈리면 같은 달인데 두 화면이 다른 숫자를 보여준다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("기본 통계 대시보드(19p)")
class DashboardOverviewIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-OV" + (System.nanoTime() % 1_000_000L);
    /** 다른 테스트의 전역 연간 집계와 겹치지 않는 해. */
    private static final int YEAR = 2041;

    private Long dealer;
    private Long mockBook;

    @BeforeAll
    void seed() {
        token();
        Long sup = createId("/masters/clients", Map.of("code", "OVS" + SFX, "name", "인쇄", "type", "NORMAL"));
        // 거래처구분 '특약점' — KPI '특약점 당월매출'의 원천
        dealer = createId("/masters/clients", Map.of("code", "OVD" + SFX, "name", "특약점A",
                "type", "NORMAL", "clientCategory", "특약점"));
        Long etcPartner = createId("/masters/clients", Map.of("code", "OVE" + SFX, "name", "학원B",
                "type", "NORMAL", "clientCategory", "기타학원"));
        Long wh = createId("/masters/warehouses", Map.of("code", "OVW" + SFX, "name", "물류", "type", "MAIN"));

        // 대분류가 다른 두 상품 → 점유율 1위 판정에 쓰인다
        mockBook = createId("/masters/products", Map.of("code", "OVM" + SFX, "name", "모의고사상품",
                "contentType", "SELF", "price", 10000, "supplyRate", 100, "salesDivision", "모의고사"));
        Long textBook = createId("/masters/products", Map.of("code", "OVT" + SFX, "name", "교재상품",
                "contentType", "SELF", "price", 10000, "supplyRate", 100, "salesDivision", "교재"));

        post("/stock/inbound", Map.of("processedDate", YEAR + "-01-01", "supplierClientId", sup,
                "destinationWarehouseId", wh,
                "items", List.of(Map.of("productId", mockBook, "unitCost", 1000, "qty", 900),
                        Map.of("productId", textBook, "unitCost", 1000, "qty", 900))));

        // 5월: 모의고사 60만(특약점) + 교재 20만(학원) → 모의고사가 점유율 1위
        sale(YEAR + "-05-10", dealer, wh, mockBook, 60);
        sale(YEAR + "-05-10", etcPartner, wh, textBook, 20);
        // 6월: 교재 10만
        sale(YEAR + "-06-10", etcPartner, wh, textBook, 10);
    }

    @Test
    @DisplayName("19p와 20p가 같은 숫자를 낸다 — 원천 공유 조건")
    void 원천_공유() {
        JsonNode ov = data(get("/dashboard/overview?year=" + YEAR + "&month=5"));
        JsonNode detail = data(get("/dashboard/sales?year=" + YEAR)).path("months");

        long detailMay = detail.get(4).path("actual").asLong();          // 5월
        assertThat(ov.path("kpi").path("monthNetSales").asLong())
                .as("19p 당월 = 20p 5월 실적").isEqualTo(detailMay);

        // 월별 트렌드도 같은 계산을 재사용한다
        assertThat(ov.path("monthlyTrend").get(4).path("netSales").asLong()).isEqualTo(detailMay);
        assertThat(ov.path("monthlyTrend").get(4).path("cumulative").asLong())
                .isEqualTo(detail.get(4).path("cumulativeActual").asLong());
    }

    @Test
    @DisplayName("KPI — 당월·누적·특약점 당월·점유율 1위 상품군")
    void kpi() {
        JsonNode k = data(get("/dashboard/overview?year=" + YEAR + "&month=5")).path("kpi");

        assertThat(k.path("monthNetSales").asLong()).isEqualTo(800_000);      // 60만 + 20만
        assertThat(k.path("cumulativeNetSales").asLong()).isEqualTo(800_000); // 1~5월
        assertThat(k.path("dealerMonthNetSales").asLong())
                .as("거래처구분 '특약점'만").isEqualTo(600_000);

        // 점유율 1위는 계산해서 낸다 — 이름을 코드에 박지 않는다
        assertThat(k.path("topCategoryName").asText()).isEqualTo("모의고사");
        assertThat(k.path("topCategoryNetSales").asLong()).isEqualTo(600_000);
        assertThat(k.path("topCategorySharePct").asDouble()).isEqualTo(75.0);   // 60/80
    }

    @Test
    @DisplayName("누적 범위는 기준월까지다 — 6월로 보면 6월분이 더해진다")
    void 누적_범위() {
        JsonNode may = data(get("/dashboard/overview?year=" + YEAR + "&month=5")).path("kpi");
        JsonNode jun = data(get("/dashboard/overview?year=" + YEAR + "&month=6")).path("kpi");

        assertThat(may.path("cumulativeNetSales").asLong()).isEqualTo(800_000);
        assertThat(jun.path("cumulativeNetSales").asLong()).isEqualTo(900_000);
        assertThat(jun.path("monthNetSales").asLong()).isEqualTo(100_000);
    }

    @Test
    @DisplayName("거래처 비중·TOP5 — 비중 합이 100%에 수렴하고 큰 순으로 나온다")
    void 비중_TOP5() {
        JsonNode ov = data(get("/dashboard/overview?year=" + YEAR + "&month=12"));

        JsonNode shares = ov.path("partnerShares");
        assertThat(shares).isNotEmpty();
        // 큰 순 정렬
        long prev = Long.MAX_VALUE;
        for (JsonNode sNode : shares) {
            long v = sNode.path("netSales").asLong();
            assertThat(v).isLessThanOrEqualTo(prev);
            prev = v;
        }
        JsonNode top = ov.path("topProducts");
        assertThat(top.size()).isLessThanOrEqualTo(5);
        assertThat(top.get(0).path("netSales").asLong()).isEqualTo(600_000);   // 모의고사상품
    }

    @Test
    @DisplayName("제품별 목표대비는 목표가 등록된 상품만 나온다")
    void 제품별_목표대비() {
        post("/dashboard/targets", Map.of("year", YEAR, "scope", "PRODUCT",
                "productId", mockBook, "targetAmount", 1_000_000));

        JsonNode rows = data(get("/dashboard/overview?year=" + YEAR + "&month=12"))
                .path("productTargets");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).path("productId").asLong()).isEqualTo(mockBook);
        assertThat(rows.get(0).path("target").asLong()).isEqualTo(1_000_000);
        assertThat(rows.get(0).path("actual").asLong()).isEqualTo(600_000);
        assertThat(rows.get(0).path("achievementPct").asDouble()).isEqualTo(60.0);
    }

    private void sale(String date, Long partnerId, Long wh, Long productId, int qty) {
        JsonNode r = post("/sales/entries", Map.of(
                "salesDate", date, "partnerId", partnerId, "warehouseId", wh,
                "items", List.of(Map.of("productId", productId, "shipmentType", "NORMAL_SHIP",
                        "unitPrice", 10000, "supplyRate", 100, "qty", qty))));
        assertThat(r.path("success").asBoolean()).as("매출등록 성공: %s", r).isTrue();
    }
}
