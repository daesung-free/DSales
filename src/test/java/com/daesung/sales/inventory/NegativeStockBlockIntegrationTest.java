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
 * 재고 음수 차단 회귀 고정 — 개발팀 점검(2026-09-11).
 *
 * <p>발단: 실서버에서 현재고 <b>5,006</b>인 도서를 <b>99,999</b> 폐기했는데 그대로 통과해
 * 잔량이 <b>−94,993</b>이 됐다. 되돌릴 방법은 역분개뿐이고, 그동안 수불부·순매출이 전부 틀린다.
 *
 * <p>★이 테스트가 고정하려는 것은 <b>차단 지점이 하나</b>라는 사실이다.
 * 재고를 깎는 경로는 폐기·매출출고·창고이고·세트조립 넷인데 모두
 * {@code InventoryService.applyDelta} 를 지난다. 넷을 다 걸어 두는 이유는,
 * 나중에 누가 경로를 하나 더 만들면서 원자적 UPDATE를 우회하면
 * <b>그 경로만 조용히 뚫리기</b> 때문이다.
 *
 * <p>⚠️차단 자체는 지시가 갈린 항목이다(발주처 2026-08-31은 "음수 허용"). 설정
 * {@code daesung.inventory.allow-negative-stock=true} 로 되돌릴 수 있고, 이 테스트는
 * <b>기본값(차단)</b>을 고정한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// ★재고를 깎아 가며 검증하므로 순서가 있다. 각 테스트는 직전 잔량을 기준으로 판단한다.
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
@DisplayName("재고 음수 차단(2026-09-11 점검)")
class NegativeStockBlockIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-NS" + (System.nanoTime() % 1_000_000L);
    private static final int YEAR = 2074;

    private Long wh;
    private Long wh2;
    private Long book;

    @BeforeAll
    void seed() {
        token();
        Long sup = createId("/masters/clients",
                Map.of("code", "NSS" + SFX, "name", "인쇄소", "type", "NORMAL"));
        wh = createId("/masters/warehouses", Map.of("code", "NSW" + SFX, "name", "음수창고", "type", "MAIN"));
        wh2 = createId("/masters/warehouses", Map.of("code", "NSW2" + SFX, "name", "음수창고2", "type", "MAIN"));

        Map<String, Object> b = new HashMap<>();
        b.put("code", "NSB" + SFX);
        b.put("name", "음수도서");
        b.put("contentType", "SELF");
        b.put("price", 10000);
        b.put("supplyRate", 70);
        b.put("catCode", "N" + YEAR + "A");
        b.put("catName", "음수분류");
        book = createId("/masters/products", b);

        // 딱 100부만 넣는다 — 경계가 분명해야 "막혔다"가 무슨 뜻인지 읽힌다.
        post("/stock/inbound", Map.of("processedDate", YEAR + "-01-05", "supplierClientId", sup,
                "destinationWarehouseId", wh,
                "items", List.of(Map.of("productId", book, "unitCost", 3000, "qty", 100))));
    }

    /** 창고별 현재고. ‼️기간을 안 주면 기본 범위가 올해라 2074년 이벤트가 통째로 빠진다. */
    private int balance(Long warehouseId) {
        String range = "?fromDate=" + YEAR + "-01-01&toDate=" + YEAR + "-12-31";
        for (JsonNode r : data(get("/stock/ledger" + range + "&warehouseId=" + warehouseId
                + "&keyword=NSB" + SFX + "&size=50")).path("content")) {
            if (("NSB" + SFX).equals(r.path("productCode").asText())) {
                return r.path("closing").asInt();
            }
        }
        return 0;
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("★현재고보다 많은 폐기는 거부되고 재고는 그대로 — 점검에서 터진 바로 그 건")
    void 폐기_초과() {
        int before = balance(wh);

        JsonNode r = post("/disposals", Map.of("processedDate", YEAR + "-02-01", "warehouseId", wh,
                "items", List.of(Map.of("productId", book, "qty", 99_999, "reason", "초과폐기"))));

        assertThat(r.path("success").asBoolean()).as("%s", r).isFalse();
        assertThat(r.path("error").path("message").asText())
                .as("현재고와 요청량을 알려 줘야 담당자가 고칠 수 있다")
                .contains("재고가 부족", String.valueOf(before), "99999");

        assertThat(balance(wh)).as("거부됐으면 재고는 손대지 않은 그대로여야 한다").isEqualTo(before);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("재고 범위 안의 폐기는 통과한다 — 막는 게 목적이 아니라 넘는 것만 막는 것")
    void 폐기_정상() {
        int before = balance(wh);

        JsonNode r = post("/disposals", Map.of("processedDate", YEAR + "-02-02", "warehouseId", wh,
                "items", List.of(Map.of("productId", book, "qty", 10, "reason", "파손"))));

        assertThat(r.path("success").asBoolean()).as("%s", r).isTrue();
        assertThat(balance(wh)).isEqualTo(before - 10);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("★재고보다 많은 매출출고도 거부 — 폐기만 막으면 옆문이 열려 있다")
    void 매출출고_초과() {
        Long partner = createId("/masters/clients",
                Map.of("code", "NSP" + SFX, "name", "음수거래처", "type", "NORMAL"));

        JsonNode r = post("/sales/entries", Map.of(
                "salesDate", YEAR + "-03-01", "partnerId", partner, "warehouseId", wh,
                "items", List.of(Map.of("productId", book, "shipmentType", "NORMAL_SHIP",
                        "unitPrice", 10000, "supplyRate", 70, "qty", 5000))));

        assertThat(r.path("success").asBoolean()).as("%s", r).isFalse();
        assertThat(r.path("error").path("message").asText()).contains("재고가 부족");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("★재고보다 많은 창고이고도 거부 — 없는 물건은 옮길 수도 없다")
    void 이고_초과() {
        int before = balance(wh);

        JsonNode r = post("/stock/transfer", Map.of(
                "processedDate", YEAR + "-03-02", "fromWarehouseId", wh, "toWarehouseId", wh2,
                "items", List.of(Map.of("productId", book, "qty", 5000))));

        assertThat(r.path("success").asBoolean()).as("%s", r).isFalse();
        assertThat(r.path("error").path("message").asText()).contains("재고가 부족");

        // ‼️이고는 차감·가산 두 번 움직인다. 한쪽만 돌고 끊기면 재고가 허공에서 늘어난다.
        assertThat(balance(wh)).as("거부 시 출발 창고 재고 그대로").isEqualTo(before);
        assertThat(balance(wh2)).as("도착 창고에도 아무것도 생기면 안 된다").isZero();
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("★위탁창고는 막지 않는다 — 초과정산 허용(발주처 2026-08-31)을 되돌리면 안 된다")
    void 위탁창고는_예외() {
        Long owner = createId("/masters/clients",
                Map.of("code", "NSO" + SFX, "name", "위탁처", "type", "NORMAL"));
        Long consign = createId("/masters/warehouses", Map.of(
                "code", "NSC" + SFX, "name", "위탁창고", "type", "CONSIGN",
                "physicalStock", false, "ownerClientId", owner));

        // 입고 한 번 없는 위탁창고에서 바로 빼 본다 — 실물 창고였다면 막혔을 상황
        JsonNode r = post("/stock/transfer", Map.of(
                "processedDate", YEAR + "-04-01", "fromWarehouseId", consign, "toWarehouseId", wh2,
                "items", List.of(Map.of("productId", book, "qty", 7))));

        assertThat(r.path("success").asBoolean())
                .as("가상 창고의 음수는 시점 차이지 오입력이 아니다: %s", r).isTrue();
        assertThat(balance(consign)).as("음수 그대로 남아야 보인다").isEqualTo(-7);
    }
}
