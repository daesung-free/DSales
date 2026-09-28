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
 * 자재 재고(V78) — 입고·잔량, 그리고 <b>도서 리포트를 흔들지 않는지</b>.
 *
 * <p>근거: 9/27 회의 A-1(항목 4·5·6·22) — "dsre는 매출만 변경, 매출에서는 수불·자재 다 관리" /
 * "자재별로 입고 가능하게".
 *
 * <p>★<b>이 테스트의 절반은 격리 확인이다.</b> 자재를 도서와 <b>같은 원장</b>({@code inventory_txn})에
 * 넣기로 했기 때문에, 자재 거래가 도서 리포트 숫자에 새어 들어갈 위험이 생겼다.
 * 대부분의 리포트는 products 를 조인해 자연히 걸러지지만 그렇지 않은 집계도 있어,
 * "사람이 매번 기억한다"에 기대지 않고 <b>같은 값이 나오는지</b>를 여기서 고정한다.
 * 새 리포트를 만들 때 이 테스트가 깨지면 그 쿼리에 자재가 새고 있다는 뜻이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("자재 재고 — 입고·잔량·도서 리포트 격리")
class MaterialStockIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-MS" + (System.nanoTime() % 1_000_000L);
    private static final String DATE = "2092-03-10";

    private Long warehouse;
    private Long supplier;
    private Long book;
    private Long material;      // 자재_입고 전용
    private Long material2;     // 자재구분_필터 전용
    private Long material3;     // 자재전표_취소 전용
    private Long material4;     // 도서리포트_격리 전용

    @BeforeAll
    void seed() {
        token();
        warehouse = createId("/masters/warehouses",
                Map.of("code", "MSW" + SFX, "name", "자재창고", "type", "MAIN"));
        supplier = createId("/masters/clients",
                Map.of("code", "MSS" + SFX, "name", "인쇄소", "type", "NORMAL"));

        Map<String, Object> b = new HashMap<>();
        b.put("code", "MSB" + SFX);
        b.put("name", "자재격리검증 교재");
        b.put("contentType", "SELF");
        b.put("price", 10000);
        b.put("supplyRate", 70);
        b.put("catCode", "X2092A01");
        b.put("catName", "격리검증분류");
        book = createId("/masters/products", b);

        material = createId("/masters/materials", Map.of(
                "code", "MSM1" + SFX, "name", "격리검증 시험지", "materialType", "시험지"));
        material2 = createId("/masters/materials", Map.of(
                "code", "MSM2" + SFX, "name", "격리검증 OMR", "materialType", "OMR"));
        // ‼️테스트마다 자재를 따로 쓴다. 하나를 공유하면 실행 순서에 따라 수량이 섞여
        //   "누적된다" 같은 검증이 다른 테스트의 입고량까지 세게 된다(실제로 그렇게 깨졌다).
        material3 = createId("/masters/materials", Map.of(
                "code", "MSM3" + SFX, "name", "격리검증 라벨", "materialType", "라벨"));
        material4 = createId("/masters/materials", Map.of(
                "code", "MSM4" + SFX, "name", "격리검증 해설지", "materialType", "해설지"));
    }

    private JsonNode materialInbound(Long materialId, int qty, Long unitCost) {
        Map<String, Object> item = new HashMap<>();
        item.put("materialId", materialId);
        item.put("qty", qty);
        item.put("unitCost", unitCost);
        return post("/stock/materials/inbound", Map.of(
                "processedDate", DATE, "supplierClientId", supplier,
                "destinationWarehouseId", warehouse, "items", List.of(item)));
    }

    private int materialQty(Long materialId) {
        JsonNode rows = data(get("/stock/materials?materialId=" + materialId
                + "&warehouseId=" + warehouse));
        return rows.isEmpty() ? 0 : rows.get(0).path("qty").asInt();
    }

    @Test
    @DisplayName("★자재를 입고하면 자재 잔량이 는다 — 도서와 같은 원장, 다른 축")
    void 자재_입고() {
        JsonNode r = materialInbound(material, 5000, 120L);

        assertThat(r.path("success").asBoolean()).as("%s", r).isTrue();
        JsonNode line = data(r).path("lines").get(0);
        assertThat(data(r).path("inboundNo").asText()).startsWith("IN-");
        assertThat(line.path("qty").asInt()).isEqualTo(5000);
        assertThat(line.path("currentQty").asInt()).isEqualTo(5000);

        materialInbound(material, 300, 120L);
        assertThat(materialQty(material)).as("누적된다").isEqualTo(5300);
    }

    @Test
    @DisplayName("자재구분으로 걸러진다 — 자재가 늘면 목록에서 찾을 수 있어야 한다")
    void 자재구분_필터() {
        materialInbound(material2, 700, 30L);

        JsonNode omr = data(get("/stock/materials?materialId=" + material2 + "&materialType=OMR"));
        assertThat(omr).hasSize(1);
        assertThat(omr.get(0).path("qty").asInt()).isEqualTo(700);

        // 한글 자재구분도 받는다(화면이 목록에서 고른 한글을 그대로 되보낸다)
        assertThat(data(get("/stock/materials?materialId=" + material2 + "&materialType=OMR")))
                .hasSize(1);
        assertThat(data(get("/stock/materials?materialId=" + material2 + "&materialType=LABEL")))
                .as("다른 구분으로는 안 잡힌다").isEmpty();
    }

    @Test
    @DisplayName("없는 자재는 404 — 조용히 0으로 넘기지 않는다")
    void 없는_자재() {
        JsonNode r = post("/stock/materials/inbound", Map.of(
                "processedDate", DATE, "supplierClientId", supplier,
                "destinationWarehouseId", warehouse,
                "items", List.of(Map.of("materialId", 99_999_999L, "qty", 1))));

        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").path("message").asText()).contains("자재가 없습니다");
    }

    @Test
    @DisplayName("★자재 전표도 취소된다 — 예전 구조라면 여기서 500이 났다")
    void 자재전표_취소() {
        int before = materialQty(material3);
        String refNo = data(materialInbound(material3, 900, 100L)).path("inboundNo").asText();
        assertThat(materialQty(material3)).isEqualTo(before + 900);

        JsonNode c = post("/stock/vouchers/" + refNo + "/cancel?reason=오입력", Map.of());
        assertThat(c.path("success").asBoolean()).as("%s", c).isTrue();
        assertThat(materialQty(material3)).as("되돌아온다").isEqualTo(before);
    }

    @Test
    @DisplayName("★★자재 거래를 넣어도 도서 리포트 숫자가 그대로다 — 같은 원장을 쓰는 대가를 여기서 막는다")
    void 도서리포트_격리() {
        // 도서 쪽 기준값을 먼저 찍는다.
        post("/stock/inbound", Map.of("processedDate", DATE, "supplierClientId", supplier,
                "destinationWarehouseId", warehouse, "inboundType", "PURCHASE",
                "items", List.of(Map.of("productId", book, "unitCost", 3000, "qty", 100))));

        String period = "fromDate=2092-01-01&toDate=2092-12-31";
        long ledgerBefore = ledgerClosing();
        String netBefore = data(get("/sales/net-summary?" + period)).toString();
        int disposalsBefore = data(get("/disposals?" + period)).size();
        int recordsBefore = data(get("/stock/records?" + period)).size();
        String bookInoutBefore = data(get("/sales/book-inout?" + period)).toString();

        // 자재를 잔뜩 넣는다 — 매입입고로도 넣어 본다(집계에 가장 새기 쉬운 경로).
        materialInbound(material4, 12_345, 999L);
        post("/stock/materials/inbound", Map.of(
                "processedDate", DATE, "supplierClientId", supplier,
                "destinationWarehouseId", warehouse, "inboundType", "PURCHASE",
                "items", List.of(Map.of("materialId", material4, "qty", 7_777, "unitCost", 555))));

        assertThat(ledgerClosing()).as("제품수불부 현재고").isEqualTo(ledgerBefore);
        assertThat(data(get("/sales/net-summary?" + period)).toString())
                .as("순매출·매입 집계").isEqualTo(netBefore);
        assertThat(data(get("/disposals?" + period)).size())
                .as("폐기 내역 건수").isEqualTo(disposalsBefore);
        assertThat(data(get("/stock/records?" + period)).size())
                .as("입고/대체 내역 — 도서 화면이라 자재가 섞이면 안 된다").isEqualTo(recordsBefore);
        assertThat(data(get("/sales/book-inout?" + period)).toString())
                .as("도서입출고현황").isEqualTo(bookInoutBefore);
    }

    /** 검증용 도서의 수불부 현재고. */
    private long ledgerClosing() {
        JsonNode rows = data(get("/stock/ledger?fromDate=2092-01-01&toDate=2092-12-31"
                + "&productId=" + book));
        long sum = 0;
        for (JsonNode r : rows) {
            sum += r.path("closing").asLong();
        }
        return sum;
    }
}
