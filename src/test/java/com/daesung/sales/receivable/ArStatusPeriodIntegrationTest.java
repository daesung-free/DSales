package com.daesung.sales.receivable;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 미수금현황의 <b>기간 조건</b>(프론트 실측 2026-10-15 A-1).
 *
 * <p>★<b>같은 날짜의 잔액은 시작일을 어떻게 잡든 같아야 한다.</b> 그게 '잔액'의 뜻이다.
 * 예전엔 기초(opening)에 <b>그해 이월만</b> 넣고 1/1~시작 전날 거래를 빼먹어서,
 * 7월부터 조회하면 상반기 거래가 통째로 사라진 잔액이 나왔다
 * (실측: 1/1 시작 75,268,510 vs 7/1 시작 59,096,620).
 *
 * <p>‼️같은 파일의 외상매출장({@code arLedger})은 처음부터 맞게 계산하고 있었다 —
 * <b>두 화면이 같은 거래처의 잔액을 다르게 보여주고 있었다.</b> 둘 중 하나만 보면 못 찾는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("미수금현황 기간 조건(2026-10-15 A-1)")
class ArStatusPeriodIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-AP" + (System.nanoTime() % 1_000_000L);
    private static final String YEAR = "2107";

    private Long partner;
    private Long product;
    private Long warehouse;

    @BeforeAll
    void seed() {
        token();
        Long supplier = createId("/masters/clients",
                Map.of("code", "APS" + SFX, "name", "인쇄소", "type", "NORMAL"));
        partner = createId("/masters/clients",
                Map.of("code", "APP" + SFX, "name", "기간검증거래처", "type", "NORMAL"));
        warehouse = createId("/masters/warehouses",
                Map.of("code", "APW" + SFX, "name", "기간검증창고", "type", "MAIN"));

        Map<String, Object> b = new HashMap<>();
        b.put("code", "APB" + SFX);
        b.put("name", "기간검증 교재");
        b.put("contentType", "SELF");
        b.put("price", 10000);
        b.put("supplyRate", 70);
        b.put("catCode", "A2107A01");
        b.put("catName", "기간검증분류");
        product = createId("/masters/products", b);

        post("/stock/inbound", Map.of("processedDate", YEAR + "-01-05", "supplierClientId", supplier,
                "destinationWarehouseId", warehouse,
                "items", List.of(Map.of("productId", product, "unitCost", 1000, "qty", 1000))));

        // 상반기 100부(70만) · 하반기 50부(35만). 수금은 상반기에 20만.
        sale(YEAR + "-03-10", 100);
        collect(YEAR + "-04-20", 200_000);
        sale(YEAR + "-09-10", 50);
    }

    private void sale(String date, int qty) {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", product);
        item.put("shipmentType", "NORMAL_SHIP");
        item.put("unitPrice", 10000);
        item.put("supplyRate", 70);
        item.put("qty", qty);
        JsonNode r = post("/sales/entries", Map.of(
                "salesDate", date, "partnerId", partner, "warehouseId", warehouse,
                "items", List.of(item)));
        assertThat(r.path("success").asBoolean()).as("매출: %s", r).isTrue();
    }

    private void collect(String date, long amount) {
        JsonNode r = post("/closing/collections", Map.of(
                "collDate", date, "partnerId", partner, "collType", "CASH", "collAmt", amount));
        assertThat(r.path("success").asBoolean()).as("수금: %s", r).isTrue();
    }

    /** 그 거래처 행. */
    private JsonNode row(String from, String to) {
        JsonNode rows = data(get("/closing/ar-status?fromDate=" + from + "&toDate=" + to
                + "&partnerId=" + partner)).path("rows");
        for (JsonNode r : rows) {
            if (r.hasNonNull("partnerId") && r.path("partnerId").asLong() == partner) {
                return r;
            }
        }
        throw new AssertionError("거래처 행 없음: " + rows);
    }

    @Test
    @DisplayName("★★같은 날짜의 잔액은 시작일을 바꿔도 같다")
    void 시작일이_잔액을_바꾸지_않는다() {
        String end = YEAR + "-12-31";

        long full = row(YEAR + "-01-01", end).path("balance").asLong();
        long half = row(YEAR + "-07-01", end).path("balance").asLong();
        long late = row(YEAR + "-10-01", end).path("balance").asLong();

        // 700,000 − 200,000 + 350,000 = 850,000
        assertThat(full).as("연초부터").isEqualTo(850_000);
        assertThat(half).as("하반기부터 — 같은 12/31 잔액이다").isEqualTo(full);
        assertThat(late).as("10월부터 — 역시 같다").isEqualTo(full);
    }

    @Test
    @DisplayName("★기초는 '시작일 전날까지의 잔액'이다 — 그해 이월만이 아니다")
    void 기초는_시작일_전날까지() {
        JsonNode half = row(YEAR + "-07-01", YEAR + "-12-31");

        // 상반기: 매출 700,000 − 수금 200,000 = 500,000 이 기초로 넘어와야 한다.
        assertThat(half.path("opening").asLong()).isEqualTo(500_000);
        // 기간 내(하반기) 발생분만 따로 잡힌다.
        assertThat(half.path("receivableGen").asLong()).isEqualTo(350_000);
        assertThat(half.path("balance").asLong()).isEqualTo(850_000);
    }

    @Test
    @DisplayName("★★기간 전에만 거래한 거래처도 잔액이 남아 있으면 보인다")
    void 기간전_거래만_있어도_보인다() {
        // 11~12월엔 거래가 없다. 그래도 미수가 남아 있으므로 목록에 있어야 한다 —
        // 빠지면 "11월부터 조회"한 담당자가 받을 돈을 못 본다.
        JsonNode r = row(YEAR + "-11-01", YEAR + "-12-31");

        assertThat(r.path("opening").asLong()).isEqualTo(850_000);
        assertThat(r.path("receivableGen").asLong()).as("기간 내 발생 없음").isZero();
        assertThat(r.path("balance").asLong()).isEqualTo(850_000);
    }

    @Test
    @DisplayName("★미수금현황과 외상매출장이 같은 잔액을 준다 — 갈리면 둘 다 못 믿는다")
    void 두_화면이_같은_값() {
        String from = YEAR + "-07-01";
        String to = YEAR + "-12-31";

        long status = row(from, to).path("balance").asLong();
        long ledger = data(get("/closing/ar-ledger?partnerId=" + partner
                + "&fromDate=" + from + "&toDate=" + to)).path("closing").asLong();

        assertThat(status).as("미수금현황 = 외상매출장").isEqualTo(ledger);
    }
}
