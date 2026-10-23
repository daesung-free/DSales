package com.daesung.sales.inventory;

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
 * 마감된 달에는 재고 <b>등록</b>도 막는다(프론트 실측 2026-10-22 ④).
 *
 * <p>‼️예전엔 취소·삭제·수정만 막혀 있었다. 그래서 <b>마감월에 새로 넣는 것은 되는데
 * 되돌릴 수는 없는</b> 상태였다 — 잘못 넣으면 고칠 길이 없다. 둘 다 막히거나 둘 다 열리거나여야 한다.
 *
 * <p>매출·수금·위탁·물류비·세무는 처음부터 등록을 막고 있었다. 재고만 예외일 이유가 없었다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("마감월 재고 등록 차단(입고·이고·폐기·실사)")
class StockPeriodLockIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-PL" + (System.nanoTime() % 1_000_000L);
    private static final int YEAR = 2096;
    private static final int MONTH = 4;
    private static final String DATE = YEAR + "-04-10";

    private Long warehouse;
    private Long warehouse2;
    private Long book;
    private Long supplier;

    @BeforeAll
    void seed() {
        token();
        supplier = createId("/masters/clients",
                Map.of("code", "PLS" + SFX, "name", "인쇄소", "type", "NORMAL"));
        warehouse = createId("/masters/warehouses",
                Map.of("code", "PLW" + SFX, "name", "마감검증창고", "type", "MAIN"));
        warehouse2 = createId("/masters/warehouses",
                Map.of("code", "PLX" + SFX, "name", "마감검증창고2", "type", "MAIN"));

        Map<String, Object> b = new HashMap<>();
        b.put("code", "PLB" + SFX);
        b.put("name", "마감검증 교재");
        b.put("contentType", "SELF");
        b.put("price", 10000);
        b.put("catCode", "P2096A01");
        b.put("catName", "마감검증");
        b.put("taxFree", false);
        b.put("useYn", true);
        b.put("set", false);
        book = createId("/masters/products", b);

        // 마감 전에 재고를 미리 넣어 둔다(이고·폐기·실사가 재고 부족으로 막히면 검증이 흐려진다).
        assertThat(inbound(YEAR + "-03-02").path("success").asBoolean()).isTrue();
    }

    private JsonNode inbound(String date) {
        return post("/stock/inbound", Map.of(
                "processedDate", date, "supplierClientId", supplier,
                "destinationWarehouseId", warehouse,
                "items", List.of(Map.of("productId", book, "unitCost", 1000, "qty", 500))));
    }

    private static void assertLocked(JsonNode r, String what) {
        assertThat(r.path("success").asBoolean()).as("%s 는 마감월에 막혀야 한다: %s", what, r).isFalse();
        assertThat(r.path("error").path("code").asText()).as(what).isEqualTo("PERIOD_LOCKED");
    }

    @Test
    @DisplayName("입고·이고·폐기·실사 등록이 모두 PERIOD_LOCKED")
    void 마감월에는_등록도_막힌다() {
        post("/closing/periods/lock", Map.of("year", YEAR, "month", MONTH, "memo", "재고등록 차단 검증"));
        try {
            assertLocked(inbound(DATE), "입고");

            assertLocked(post("/stock/transfer", Map.of(
                    "processedDate", DATE, "fromWarehouseId", warehouse, "toWarehouseId", warehouse2,
                    "items", List.of(Map.of("productId", book, "qty", 10)))), "이고");

            assertLocked(post("/disposals", Map.of(
                    "processedDate", DATE, "warehouseId", warehouse,
                    "items", List.of(Map.of("productId", book, "qty", 5, "reason", "파손")))), "폐기");

            assertLocked(post("/stock/stocktakes", Map.of(
                    "stocktakeDate", DATE, "warehouseId", warehouse,
                    "items", List.of(Map.of("productId", book, "countedQty", 490)))), "실사");
        } finally {
            post("/closing/periods/unlock", Map.of("year", YEAR, "month", MONTH, "memo", "테스트 정리"));
        }

        // ★마감을 풀면 다시 된다 — 막는 것이 날짜가 아니라 **마감 상태**임을 확인한다.
        assertThat(inbound(DATE).path("success").asBoolean()).as("마감 해제 후 입고").isTrue();
    }

    @Test
    @DisplayName("마감되지 않은 달은 그대로 등록된다 — 과하게 막지 않았는지 함께 본다")
    void 마감_안된_달은_된다() {
        assertThat(inbound(YEAR + "-05-10").path("success").asBoolean()).isTrue();
    }
}
