package com.daesung.sales.sale;

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
 * 매출액정리(화면13) 거래처·키워드 필터. 근거: 발주처 회신 2026-09-18 —
 *
 * <pre>
 *   결과 테이블 = 분류코드·분류명·도서코드·도서명·수량·공급가액·세액·총금액 (8컬럼)
 *   구분(매출/반품/교사용/증정)·매출유형(일반/위탁) → 컬럼에서 빼고 "필터"로만
 *   소계(분류별)·분류계(대분류별) 롤업 → 유지
 * </pre>
 *
 * <p>이 구조는 {@code /sales/statement}가 이미 갖고 있었는데 <b>거래처 필터와 검색이 없었다</b>.
 * 매출액정리 화면에는 둘 다 있으므로, 그대로 옮기면 기존 기능을 잃는다. 그 둘을 채웠다.
 *
 * <p>★<b>키워드는 롤업을 만들기 전에 건다.</b> 만들고 나서 상세행만 걸러내면 소계·분류계·총계가
 * 걸러지기 전 값으로 남아, 화면에서 상세를 다 더해도 소계와 안 맞는다.
 * 이 테스트가 "상세 합 = 소계 = 총계"를 고정한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("매출액정리 거래처·키워드 필터(화면13)")
class StatementFilterIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-SF" + (System.nanoTime() % 1_000_000L);
    private static final int YEAR = 2091;
    private static final String RANGE = "?fromDate=" + YEAR + "-01-01&toDate=" + YEAR + "-12-31";

    private Long partnerA;
    private Long partnerB;

    @BeforeAll
    void seed() {
        token();
        Long sup = createId("/masters/clients",
                Map.of("code", "SFS" + SFX, "name", "인쇄소", "type", "NORMAL"));
        partnerA = createId("/masters/clients",
                Map.of("code", "SFA" + SFX, "name", "알파상사", "type", "NORMAL"));
        partnerB = createId("/masters/clients",
                Map.of("code", "SFB" + SFX, "name", "베타서적", "type", "NORMAL"));
        Long wh = createId("/masters/warehouses",
                Map.of("code", "SFW" + SFX, "name", "명세창고", "type", "MAIN"));

        Long kor = book("SFK" + SFX, "국어기본서", "K" + YEAR + "A", "국어분류");
        Long math = book("SFM" + SFX, "수학기본서", "M" + YEAR + "A", "수학분류");

        post("/stock/inbound", Map.of("processedDate", YEAR + "-01-05", "supplierClientId", sup,
                "destinationWarehouseId", wh,
                "items", List.of(Map.of("productId", kor, "unitCost", 1000, "qty", 1000),
                        Map.of("productId", math, "unitCost", 1000, "qty", 1000))));

        sell(wh, partnerA, kor, 10);
        sell(wh, partnerA, math, 20);
        sell(wh, partnerB, kor, 30);
    }

    private Long book(String code, String name, String catCode, String catName) {
        Map<String, Object> b = new HashMap<>();
        b.put("code", code);
        b.put("name", name);
        b.put("contentType", "SELF");
        b.put("price", 10000);
        b.put("supplyRate", 70);
        b.put("catCode", catCode);
        b.put("catName", catName);
        b.put("salesDivision", "교재");
        return createId("/masters/products", b);
    }

    private void sell(Long wh, Long partner, Long product, int qty) {
        JsonNode r = post("/sales/entries", Map.of(
                "salesDate", YEAR + "-03-10", "partnerId", partner, "warehouseId", wh,
                "items", List.of(Map.of("productId", product, "shipmentType", "NORMAL_SHIP",
                        "unitPrice", 10000, "supplyRate", 70, "qty", qty))));
        assertThat(r.path("success").asBoolean()).as("%s", r).isTrue();
    }

    private JsonNode rows(String query) {
        return data(get("/sales/statement" + RANGE + query)).path("rows");
    }

    private long sum(JsonNode rows, String rowType) {
        long t = 0;
        for (JsonNode r : rows) {
            if (rowType.equals(r.path("rowType").asText())) {
                t += r.path("qty").asLong();
            }
        }
        return t;
    }

    @Test
    @DisplayName("★거래처로 거르면 그 거래처 것만 남는다")
    void 거래처_필터() {
        JsonNode r = rows("&partnerId=" + partnerA + "&keyword=" + SFX.substring(1));

        assertThat(sum(r, "DETAIL")).as("알파상사 10+20").isEqualTo(30);
        assertThat(sum(r, "GRAND_TOTAL")).as("총계도 같이 줄어든다").isEqualTo(30);
    }

    @Test
    @DisplayName("★키워드로 걸러도 소계·총계가 상세 합과 맞는다 — 롤업을 나중에 걸면 어긋난다")
    void 키워드_롤업_정합() {
        JsonNode r = rows("&keyword=수학기본서");

        long detail = sum(r, "DETAIL");
        assertThat(detail).as("수학만 20").isEqualTo(20);
        assertThat(sum(r, "CAT_SUBTOTAL")).as("★분류 소계 = 상세 합").isEqualTo(detail);
        assertThat(sum(r, "MAJOR_TOTAL")).as("★대분류계 = 상세 합").isEqualTo(detail);
        assertThat(sum(r, "GRAND_TOTAL")).as("★총계 = 상세 합").isEqualTo(detail);
    }

    @Test
    @DisplayName("★거래처 + 키워드를 같이 주면 교집합")
    void 두_필터_교집합() {
        JsonNode r = rows("&partnerId=" + partnerB + "&keyword=국어기본서");

        assertThat(sum(r, "DETAIL")).as("베타서적 × 국어 = 30").isEqualTo(30);
        assertThat(sum(r, "GRAND_TOTAL")).isEqualTo(30);

        // 베타서적은 수학을 사지 않았다 — 교집합이 비면 상세가 없어야 한다.
        assertThat(sum(rows("&partnerId=" + partnerB + "&keyword=수학기본서"), "DETAIL")).isZero();
    }

    @Test
    @DisplayName("8컬럼 구조는 그대로 — 화면13이 요구한 칸이 다 있다")
    void 여덟_컬럼() {
        JsonNode detail = null;
        for (JsonNode r : rows("&keyword=국어기본서")) {
            if ("DETAIL".equals(r.path("rowType").asText())) {
                detail = r;
            }
        }

        assertThat(detail).isNotNull();
        for (String f : new String[]{"catCode", "catName", "bookCode", "bookName",
                "qty", "amount", "tax", "total"}) {
            assertThat(detail.has(f)).as("%s 칸", f).isTrue();
        }
    }

    @Test
    @DisplayName("엑셀도 같은 필터를 받는다")
    void 엑셀() {
        assertThat(getBytes("/sales/statement/export" + RANGE
                + "&partnerId=" + partnerA + "&keyword=수학").getBody()).isNotEmpty();
    }
}
