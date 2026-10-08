package com.daesung.sales.consignment;

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
 * 위탁출고의 <b>학교/학원 축</b>과 <b>작업요청서 연계</b>
 * (검수피드백 2026-09-30 p.12 / 프론트 [A]1).
 *
 * <p>발주처 원문 둘 —
 * <ul>
 *   <li>"위탁출고 등록 시 물류 [작업요청서] 내역에 반영되지 않는 문제가 있습니다.
 *       <b>위탁출고도 실제 출고가 발생하므로</b> 작업요청서에 포함되어야 합니다."</li>
 *   <li>"위탁창고 선택뿐 아니라 <b>하위 학교/학원까지 선택 가능</b>해야 하고 …
 *       ★위탁 미결 매출 확정 시에도 <b>세부거래처별로 나누어 확정</b>할 수 있어야 합니다★"</li>
 * </ul>
 *
 * <p>★<b>미결원장 행에 학교를 다는 것이 핵심</b>이다. 출고 전표에만 달면 "A학교 몫 70부만 확정"을
 * 표현할 수 없다 — 확정(정산)이 행 단위로 일어나기 때문이다. 행이 갈려 있으면 정산 로직을
 * 건드리지 않고도 학교별 확정이 된다. 그 구조를 여기서 고정한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("위탁출고 학교 축 · 작업요청서 연계(2026-09-30)")
class ConsignSchoolIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-CS" + (System.nanoTime() % 1_000_000L);
    private static final String DATE = "2106-07-20";
    private static final String RANGE = "?fromDate=2106-01-01&toDate=2106-12-31";

    private Long partner;
    private Long mainWh;
    private Long consignWh;
    private Long book;
    private String schoolA;
    private String schoolB;

    @BeforeAll
    void seed() {
        token();
        Long supplier = createId("/masters/clients",
                Map.of("code", "CSS" + SFX, "name", "인쇄소", "type", "NORMAL"));
        partner = createId("/masters/clients",
                Map.of("code", "CSP" + SFX, "name", "위탁학교검증거래처", "type", "NORMAL"));
        mainWh = createId("/masters/warehouses",
                Map.of("code", "CSM" + SFX, "name", "위탁검증 물류창고", "type", "MAIN"));
        consignWh = createId("/masters/warehouses",
                Map.of("code", "CSC" + SFX, "name", "위탁검증 위탁창고", "type", "CONSIGN"));

        Map<String, Object> b = new HashMap<>();
        b.put("code", "CSB" + SFX);
        b.put("name", "위탁검증 교재");
        b.put("contentType", "SELF");
        b.put("price", 10000);
        b.put("supplyRate", 70);
        b.put("catCode", "S2106A01");
        b.put("catName", "위탁검증분류");
        b.put("salesDivision", "교재");
        book = createId("/masters/products", b);

        post("/stock/inbound", Map.of("processedDate", DATE, "supplierClientId", supplier,
                "destinationWarehouseId", mainWh,
                "items", List.of(Map.of("productId", book, "unitCost", 1000, "qty", 1000))));

        schoolA = "CSA" + SFX;
        schoolB = "CSZ" + SFX;
        school(schoolA, "가학교");
        school(schoolB, "나학교");
    }

    private void school(String code, String name) {
        JsonNode r = post("/masters/schools", Map.of(
                "schoolCode", code, "custCode", "CSP" + SFX, "custName", "위탁학교검증거래처",
                "schoolName", name, "city", "서울"));
        assertThat(r.path("success").asBoolean()).as("학교 등록: %s", r).isTrue();
    }

    private void consignOut(String schoolCode, int qty) {
        Map<String, Object> body = new HashMap<>();
        body.put("processedDate", DATE);
        body.put("partnerId", partner);
        body.put("fromWarehouseId", mainWh);
        body.put("toWarehouseId", consignWh);
        body.put("schoolCode", schoolCode);
        body.put("items", List.of(Map.of("productId", book, "qty", qty)));
        JsonNode r = post("/consignment/out", body);
        assertThat(r.path("success").asBoolean()).as("위탁출고: %s", r).isTrue();
    }

    private JsonNode pending(String query) {
        return data(get("/consignment/pending?partnerId=" + partner + query)).path("items");
    }

    @Test
    @DisplayName("★★미결이 학교별로 갈린다 — 그래야 'A학교 몫만 확정'이 된다")
    void 학교별_미결() {
        consignOut(schoolA, 100);
        consignOut(schoolB, 60);

        JsonNode all = pending("");
        assertThat(all).as("두 학교 몫").hasSizeGreaterThanOrEqualTo(2);

        JsonNode onlyA = pending("&schoolCode=" + schoolA);
        assertThat(onlyA).hasSize(1);
        assertThat(onlyA.get(0).path("schoolCode").asText()).isEqualTo(schoolA);
        assertThat(onlyA.get(0).path("schoolName").asText()).isEqualTo("가학교");
        assertThat(onlyA.get(0).path("remainingQty").asInt()).isEqualTo(100);
        // 출고일이 없으면 담당자가 어느 건을 먼저 확정할지 판단할 수 없다.
        assertThat(onlyA.get(0).path("tradeDate").asText()).isEqualTo(DATE);

        // ★학교별 확정 — 정산 로직은 그대로다. 행이 갈려 있어 그 행만 확정하면 된다.
        long outId = onlyA.get(0).path("consignmentOutId").asLong();
        JsonNode settled = post("/consignment/settle", Map.of(
                "salesDate", DATE,
                "settlements", List.of(Map.of("consignmentOutId", outId, "settleQty", 70,
                        "unitPrice", 10000, "supplyRate", 70))));
        assertThat(settled.path("success").asBoolean()).as("정산: %s", settled).isTrue();

        assertThat(pending("&schoolCode=" + schoolA).get(0).path("remainingQty").asInt())
                .as("A학교만 줄어든다").isEqualTo(30);
        assertThat(pending("&schoolCode=" + schoolB).get(0).path("remainingQty").asInt())
                .as("B학교는 그대로").isEqualTo(60);
    }

    @Test
    @DisplayName("★★위탁출고도 작업요청서에 뜨고, 지시 목록이 비어 있지 않다")
    void 위탁출고_작업요청서() {
        String school = "CSW" + SFX;
        school(school, "작업요청서학교");
        consignOut(school, 40);

        JsonNode d = data(get("/logistics/work-orders" + RANGE + "&partnerId=" + partner));
        JsonNode rows = d.isArray() ? d : d.path("content");

        JsonNode mine = null;
        for (JsonNode r : rows) {
            if (school.equals(r.path("schoolCode").asText())) {
                mine = r;
            }
        }
        assertThat(mine).as("위탁출고 건이 작업요청서에 떠야 한다: %s", rows).isNotNull();

        // ‼️예전엔 발송 건은 생겨도 **지시 목록이 비어** 있었다 — 위탁출고는 매출이 없어서
        //   매출 기반 목록에 안 잡히기 때문이다. 물류는 뭘 싸야 할지 알 수 없었다.
        assertThat(mine.path("lines")).as("무엇을 몇 개 넣을지가 있어야 한다").isNotEmpty();
        assertThat(mine.path("totalQty").asLong()).isEqualTo(40);
        assertThat(mine.path("lines").get(0).path("qty").asLong()).isEqualTo(40);
        assertThat(mine.path("schoolName").asText()).isEqualTo("작업요청서학교");
    }

    @Test
    @DisplayName("학교를 안 주면 예전처럼 동작한다 — 기존 위탁출고를 깨지 않는다")
    void 학교_미지정() {
        consignOut(null, 25);

        boolean found = false;
        for (JsonNode r : pending("")) {
            if (!r.hasNonNull("schoolCode") && r.path("remainingQty").asInt() == 25) {
                found = true;
            }
        }
        assertThat(found).as("학교 없는 미결도 그대로 조회된다").isTrue();
    }
}
