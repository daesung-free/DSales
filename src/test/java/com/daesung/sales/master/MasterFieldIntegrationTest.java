package com.daesung.sales.master;

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
 * 기초마스터 확장 필드(32p) 통합테스트.
 * 도서(매출구분·수불부노출·Web게시) / 창고(실물재고여부·소속거래처) + 수불부노출 연동.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("기초마스터 확장 통합테스트")
class MasterFieldIntegrationTest extends IntegrationTestSupport {

    @BeforeAll
    void auth() {
        token();
    }

    @Test
    @DisplayName("도서 신규필드 — 매출구분·수불부노출·Web게시 저장/응답")
    void 도서_신규필드() {
        JsonNode d = data(post("/masters/products", Map.of(
                "code", "MF-BK1", "name", "확장필드 도서", "contentType", "SELF",
                "salesDivision", "정상", "ledgerVisible", false, "webVisible", true)));
        assertThat(d.path("salesDivision").asText()).isEqualTo("정상");
        assertThat(d.path("ledgerVisible").asBoolean()).isFalse();
        assertThat(d.path("webVisible").asBoolean()).isTrue();

        // 미지정 시 기본값(수불부노출 true, Web게시 false)
        JsonNode def = data(post("/masters/products", Map.of(
                "code", "MF-BK2", "name", "기본값 도서", "contentType", "SELF")));
        assertThat(def.path("ledgerVisible").asBoolean()).isTrue();
        assertThat(def.path("webVisible").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("창고 신규필드 — 실물재고여부·소속거래처 저장/응답")
    void 창고_신규필드() {
        Long owner = createId("/masters/clients",
                Map.of("code", "MF-OWN", "name", "소속거래처A", "type", "NORMAL"));
        JsonNode d = data(post("/masters/warehouses", Map.of(
                "code", "MF-WH-C", "name", "위탁창고", "type", "CONSIGN",
                "physicalStock", false, "ownerClientId", owner)));
        assertThat(d.path("physicalStock").asBoolean()).isFalse();
        assertThat(d.path("ownerClientId").asLong()).isEqualTo(owner);
        assertThat(d.path("ownerClientName").asText()).isEqualTo("소속거래처A");

        // 미지정 시 유형 기본값: CONSIGN→false
        JsonNode consign = data(post("/masters/warehouses",
                Map.of("code", "MF-WH-C2", "name", "위탁창고2", "type", "CONSIGN")));
        assertThat(consign.path("physicalStock").asBoolean()).isFalse();
        // MAIN→true
        JsonNode main = data(post("/masters/warehouses",
                Map.of("code", "MF-WH-M", "name", "물류창고", "type", "MAIN")));
        assertThat(main.path("physicalStock").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("수불부노출=false 도서는 제품수불부에서 제외")
    void 수불부노출_제외() {
        Long wh = createId("/masters/warehouses",
                Map.of("code", "MF-LWH", "name", "수불부창고", "type", "MAIN"));
        Long visible = createId("/masters/products", Map.of(
                "code", "MF-VIS", "name", "노출도서", "contentType", "SELF", "ledgerVisible", true));
        Long hidden = createId("/masters/products", Map.of(
                "code", "MF-HID", "name", "비노출도서", "contentType", "SELF", "ledgerVisible", false));
        inbound(wh, visible);
        inbound(wh, hidden);

        JsonNode rows = data(get("/stock/ledger?warehouseId=" + wh));
        assertThat(codes(rows)).contains("MF-VIS").doesNotContain("MF-HID");
    }

    private void inbound(Long whId, Long productId) {
        post("/stock/inbound", Map.of(
                "processedDate", "2026-06-01", "supplierClientId", ownerFallback(), "destinationWarehouseId", whId,
                "items", List.of(Map.of("productId", productId, "unitCost", 3000, "qty", 100))));
    }

    private Long ownerFallback = null;

    private Long ownerFallback() {
        if (ownerFallback == null) {
            ownerFallback = createId("/masters/clients",
                    Map.of("code", "MF-SUP", "name", "입고처", "type", "NORMAL"));
        }
        return ownerFallback;
    }

    private java.util.List<String> codes(JsonNode rows) {
        java.util.List<String> out = new java.util.ArrayList<>();
        rows.forEach(r -> out.add(r.path("productCode").asText()));
        return out;
    }
}
