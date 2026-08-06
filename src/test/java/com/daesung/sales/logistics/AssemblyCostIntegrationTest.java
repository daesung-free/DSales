package com.daesung.sales.logistics;

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
 * 세트 조립 작업비 자동계산 회귀 고정.
 * 근거: 발주처 확정 2026-08-05 — "물류비용등록에 등록된 단가 기준으로 시스템이 자동 계산".
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("세트 조립 작업비 통합테스트")
class AssemblyCostIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-" + (System.nanoTime() % 1_000_000L);

    private Long supplier;
    private Long warehouse;

    @BeforeAll
    void seed() {
        token();
        supplier = createId("/masters/clients", Map.of("code", "AC-SUP" + SFX, "name", "조립인쇄소", "type", "NORMAL"));
        warehouse = createId("/masters/warehouses", Map.of("code", "AC-WH" + SFX, "name", "조립창고", "type", "MAIN"));

        // 자재 단가: 시험지 120원(작업구분3), OMR 50원(공통)
        put("/logistics-costs/material-rates",
                Map.of("materialType", "EXAM_PAPER", "packType", 3, "unitRate", 120));
        put("/logistics-costs/material-rates",
                Map.of("materialType", "OMR", "unitRate", 50));
    }

    private Long product(String code, String name) {
        return createId("/masters/products",
                Map.of("code", code + SFX, "name", name, "contentType", "SELF", "price", 10000));
    }

    private void inbound(Long productId, int qty) {
        post("/stock/inbound", Map.of(
                "processedDate", "2026-06-01", "supplierClientId", supplier, "destinationWarehouseId", warehouse,
                "items", List.of(Map.of("productId", productId, "unitCost", 3000, "qty", qty))));
    }

    @Test
    @DisplayName("조립 시 작업비가 자동 계산된다 — Σ(소요수량 × 작업수량 × 자재단가)")
    void 조립_작업비_자동계산() {
        Long set = product("AC-SET", "조립세트");
        Long paper = product("AC-PAPER", "시험지");
        Long omr = product("AC-OMR", "OMR");
        inbound(paper, 1000);
        inbound(omr, 1000);

        // 세트 1개 = 시험지 2장(작업구분3) + OMR 1장(공통)
        put("/masters/products/" + set + "/bom", Map.of("components", List.of(
                Map.of("childProductId", paper, "ratio", 2, "materialType", "EXAM_PAPER", "packType", 3),
                Map.of("childProductId", omr, "ratio", 1, "materialType", "OMR"))));

        // 10세트 조립 → 시험지 2×10×120=2,400 + OMR 1×10×50=500 = 2,900
        JsonNode r = data(post("/stock/bom", Map.of(
                "processedDate", "2026-07-10", "warehouseId", warehouse,
                "direction", "ASSEMBLE", "parentProductId", set, "workQty", 10, "memo", "7월 조립")));
        assertThat(r.path("workCost").asLong()).as("조립 작업비 자동계산").isEqualTo(2_900);

        // 내역 조회 — 물류팀이 받아 가공하는 자료. 같은 클래스의 다른 테스트와 겹치지 않게 그날짜만 본다.
        JsonNode rows = data(get("/logistics-costs/assembly"
                + "?fromDate=2026-07-10&toDate=2026-07-10&warehouseId=" + warehouse));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).path("workQty").asLong()).isEqualTo(10);
        assertThat(rows.get(0).path("workCost").asLong()).isEqualTo(2_900);
        assertThat(rows.get(0).path("productName").asText()).isEqualTo("조립세트");
    }

    @Test
    @DisplayName("해체는 작업비가 없다 — 포장 작업이 발생하지 않는다")
    void 해체는_작업비_없음() {
        Long set = product("AC-SET2", "해체세트");
        Long paper = product("AC-PAPER2", "시험지2");
        inbound(paper, 1000);
        put("/masters/products/" + set + "/bom", Map.of("components", List.of(
                Map.of("childProductId", paper, "ratio", 2, "materialType", "EXAM_PAPER", "packType", 3))));

        post("/stock/bom", Map.of("processedDate", "2026-07-11", "warehouseId", warehouse,
                "direction", "ASSEMBLE", "parentProductId", set, "workQty", 5));
        JsonNode dis = data(post("/stock/bom", Map.of(
                "processedDate", "2026-07-12", "warehouseId", warehouse,
                "direction", "DISASSEMBLE", "parentProductId", set, "workQty", 2)));
        assertThat(dis.path("workCost").asLong()).isZero();
    }

    @Test
    @DisplayName("단가 미등록 자재는 0으로 계산 — 등록되면 이후 작업부터 반영된다")
    void 단가_미등록_자재() {
        Long set = product("AC-SET3", "미등록세트");
        Long label = product("AC-LABEL", "라벨");   // LABEL 단가 미등록
        inbound(label, 1000);
        put("/masters/products/" + set + "/bom", Map.of("components", List.of(
                Map.of("childProductId", label, "ratio", 3, "materialType", "LABEL"))));

        JsonNode before = data(post("/stock/bom", Map.of(
                "processedDate", "2026-07-13", "warehouseId", warehouse,
                "direction", "ASSEMBLE", "parentProductId", set, "workQty", 10)));
        assertThat(before.path("workCost").asLong()).as("단가 없으면 0").isZero();

        // 단가 등록 후 조립 → 3×10×30 = 900
        put("/logistics-costs/material-rates", Map.of("materialType", "LABEL", "unitRate", 30));
        JsonNode after = data(post("/stock/bom", Map.of(
                "processedDate", "2026-07-14", "warehouseId", warehouse,
                "direction", "ASSEMBLE", "parentProductId", set, "workQty", 10)));
        assertThat(after.path("workCost").asLong()).isEqualTo(900);

        // ★이미 끝난 작업의 금액은 소급해서 바뀌지 않는다
        JsonNode rows = data(get("/logistics-costs/assembly"
                + "?fromDate=2026-07-13&toDate=2026-07-13&warehouseId=" + warehouse));
        assertThat(rows.get(0).path("workCost").asLong())
                .as("단가를 나중에 등록해도 과거 작업비는 그대로").isZero();
    }
}
