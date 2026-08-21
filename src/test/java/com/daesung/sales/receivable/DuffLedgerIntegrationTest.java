package com.daesung.sales.receivable;

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
 * 외상매출장 '더프모만'(24p) 회귀 고정.
 *
 * <p>정본 24p: "'더프모만' 체크 시 그리드 컬럼 구조 자체가 학교(원)별/학년별/시행월별
 * 세분화 구조로 <b>완전 전환</b>됨(조건부 스키마)".
 *
 * <p>★레거시(외상매출장조회.vb:603)는 시행월·학년·처리를 <b>도서명/분류명 문자열에서 긁었고</b>,
 * 규칙에 안 맞으면 {@code '##ERROR'}를 찍었다. 우리는 셋 다 필드로 갖고 있어 파싱하지 않는다 —
 * 그것이 이 테스트가 지키는 것이다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("외상매출장 더프모만(24p)")
class DuffLedgerIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-DF" + (System.nanoTime() % 1_000_000L);
    private static final int YEAR = 2043;

    private Long partner;
    private Long mockSet;

    @BeforeAll
    void seed() {
        token();
        Long sup = createId("/masters/clients", Map.of("code", "DFS" + SFX, "name", "인쇄", "type", "NORMAL"));
        partner = createId("/masters/clients", Map.of("code", "DFP" + SFX, "name", "더프거래처", "type", "NORMAL"));
        Long wh = createId("/masters/warehouses", Map.of("code", "DFW" + SFX, "name", "물류", "type", "MAIN"));

        // 모의고사 완제품(세트) + 구성 자재 — 시행예정일은 BOM에 있다(V27)
        Long material = createId("/masters/products", Map.of("code", "DFM" + SFX, "name", "시험지",
                "contentType", "SELF", "price", 1000, "salesDivision", "모의고사"));
        // ★더프 판별 = 분류코드 M + A/B/C 계열(레거시 외상매출장조회.vb:668)
        mockSet = createId("/masters/products", Map.of("code", "DFK" + SFX, "name", "모의고사세트",
                "contentType", "SELF", "price", 10000, "supplyRate", 100,
                "grade", "고3", "catCode", "M2043A01", "catName", "더프 고3",
                "salesDivision", "모의고사", "stockManaged", false));
        put("/masters/products/" + mockSet + "/bom", Map.of("components", List.of(Map.of(
                "childProductId", material, "ratio", 1, "round", 3,
                "examDate", YEAR + "-09-05"))));

        // 교재(모의고사 아님) — 섞이면 안 된다
        Long textBook = createId("/masters/products", Map.of("code", "DFT" + SFX, "name", "교재",
                "contentType", "SELF", "price", 10000, "supplyRate", 100,
                "catCode", "H2043H01", "catName", "교재", "salesDivision", "교재"));
        // ‼️모의고사이지만 더프가 아닌 계열(M…D) — 대분류로 걸렀다면 섞여 들어왔을 건이다
        Long nonDuff = createId("/masters/products", Map.of("code", "DFN" + SFX, "name", "비더프모의고사",
                "contentType", "SELF", "price", 10000, "supplyRate", 100,
                "catCode", "M2043D01", "catName", "기타 모의고사", "salesDivision", "모의고사"));
        post("/stock/inbound", Map.of("processedDate", YEAR + "-01-01", "supplierClientId", sup,
                "destinationWarehouseId", wh,
                "items", List.of(Map.of("productId", textBook, "unitCost", 1000, "qty", 500),
                        Map.of("productId", nonDuff, "unitCost", 1000, "qty", 500))));

        // 같은 달·같은 학교 2건(처리/비처리) + 다른 학교 1건
        sale(YEAR + "-09-10", wh, mockSet, 10, "A고", "GRADED", 3);
        sale(YEAR + "-09-11", wh, mockSet, 5, "A고", null, 3);
        sale(YEAR + "-09-12", wh, mockSet, 7, "B고", "GRADED", 3);
        // 제외돼야 하는 둘: 교재 / 더프가 아닌 모의고사
        sale(YEAR + "-09-13", wh, textBook, 99, "A고", "GRADED", null);
        sale(YEAR + "-09-14", wh, nonDuff, 88, "A고", "GRADED", null);
    }

    @Test
    @DisplayName("컬럼이 통째로 다르다 — 학교(원)·시행월·학년·처리 세분화")
    void 조건부_스키마() {
        JsonNode d = detail().get(0);

        assertThat(d.path("schoolName").asText()).isEqualTo("A고");
        assertThat(d.path("grade").asText()).as("도서명 파싱이 아니라 상품 마스터 학년").isEqualTo("고3");
        assertThat(d.path("procType").asText()).isIn("처리", "비처리");
        assertThat(d.path("examMonth").asText())
                .as("BOM 시행예정일에서 온다(레거시는 도서명을 잘라 만들었다)")
                .isEqualTo(YEAR + "-09");
        assertThat(d.path("unitPrice").asInt()).isEqualTo(10000);

        // 기본 장부에 있는 '잔액'은 이 화면에 없다(잔액 개념이 없는 화면이다)
        assertThat(d.hasNonNull("balance")).isFalse();
    }

    @Test
    @DisplayName("더프만 나온다 — 교재도, 더프 아닌 모의고사(M…D)도 빠진다")
    void 더프만() {
        assertThat(detail()).hasSize(3);
        assertThat(data(ledger()).path("totalQty").asLong()).isEqualTo(22);
        // 교재 99가 섞이면 121, 비더프 모의고사 88까지 섞이면 209가 된다.
        // ‼️대분류(모의고사)로 걸렀다면 88이 섞여 110이 나왔을 것 — 더프는 모의고사의 부분집합이다.
    }

    @Test
    @DisplayName("소계가 학교(원) 계 → 월 계 순으로 붙는다")
    void 소계() {
        List<String> types = new java.util.ArrayList<>();
        data(ledger()).path("rows").forEach(r -> types.add(r.path("rowType").asText()));

        assertThat(types).contains("DETAIL", "SCHOOL_SUBTOTAL", "MONTH_SUBTOTAL");
        assertThat(types.get(types.size() - 1)).as("마지막은 월 계").isEqualTo("MONTH_SUBTOTAL");

        // 학교 계 합 = 전체(A고 15 + B고 7)
        long schoolSum = 0;
        for (JsonNode r : data(ledger()).path("rows")) {
            if ("SCHOOL_SUBTOTAL".equals(r.path("rowType").asText())) {
                schoolSum += r.path("qty").asLong();
            }
        }
        assertThat(schoolSum).isEqualTo(22);
    }

    @Test
    @DisplayName("처리/비처리는 성적처리 구분에서 온다 — 분류명 문자열이 아니다")
    void 처리구분() {
        long graded = detail().stream().filter(r -> "처리".equals(r.path("procType").asText()))
                .mapToLong(r -> r.path("qty").asLong()).sum();
        long ungraded = detail().stream().filter(r -> "비처리".equals(r.path("procType").asText()))
                .mapToLong(r -> r.path("qty").asLong()).sum();

        assertThat(graded).isEqualTo(17);    // 10 + 7
        assertThat(ungraded).isEqualTo(5);   // 미지정 = 비처리
    }

    private JsonNode ledger() {
        return get("/closing/ar-ledger/duff?partnerId=" + partner
                + "&fromDate=" + YEAR + "-01-01&toDate=" + YEAR + "-12-31");
    }

    private List<JsonNode> detail() {
        List<JsonNode> out = new java.util.ArrayList<>();
        for (JsonNode r : data(ledger()).path("rows")) {
            if ("DETAIL".equals(r.path("rowType").asText())) {
                out.add(r);
            }
        }
        return out;
    }

    private void sale(String date, Long wh, Long productId, int qty,
                      String school, String procType, Integer round) {
        Map<String, Object> item = new java.util.HashMap<>();
        item.put("productId", productId);
        item.put("shipmentType", "NORMAL_SHIP");
        item.put("unitPrice", 10000);
        item.put("supplyRate", 100);
        item.put("qty", qty);
        item.put("schoolName", school);
        if (procType != null) {
            item.put("procType", procType);
        }
        if (round != null) {
            item.put("round", round);
        }
        JsonNode r = post("/sales/entries", Map.of(
                "salesDate", date, "partnerId", partner, "warehouseId", wh,
                "items", List.of(item)));
        assertThat(r.path("success").asBoolean()).as("매출등록 성공: %s", r).isTrue();
    }
}
