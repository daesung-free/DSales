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
 * 조회 조건을 <b>서버가</b> 거르는지(2026-10-08 프론트 요청 [B]).
 *
 * <p>★<b>왜 서버로 옮겼는가</b> — 이 조건들이 없어서 화면이 기간 데이터를 통째로 받아
 * 자바스크립트로 걸러 쓰고 있었다. 데이터가 쌓이면 느려지는 정도가 아니라 <b>못 쓴다</b>
 * (한 번에 2만 행 상한에 걸리면 조용히 잘린 결과를 보여주게 된다).
 *
 * <p>여기서 고정하는 것은 "조건을 걸면 실제로 줄어드는가"다. 줄지 않으면 화면은
 * 서버가 거른 줄 알고 임시 코드를 지울 텐데, 그러면 걸러지지 않은 자료가 그대로 나간다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("조회 조건 서버 적용(2026-10-08)")
class QueryFilterIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-QF" + (System.nanoTime() % 1_000_000L);
    private static final String DATE = "2101-03-05";
    private static final String PERIOD = "fromDate=2101-01-01&toDate=2101-12-31";

    private Long partnerA;
    private Long partnerB;
    private Long bookMock;    // 세부구분 모의고사
    private Long bookText;    // 세부구분 교재

    @BeforeAll
    void seed() {
        token();
        Long supplier = createId("/masters/clients",
                Map.of("code", "QFS" + SFX, "name", "인쇄소", "type", "NORMAL"));
        partnerA = createId("/masters/clients",
                Map.of("code", "QFA" + SFX, "name", "조건검증A", "type", "NORMAL"));
        partnerB = createId("/masters/clients",
                Map.of("code", "QFB" + SFX, "name", "조건검증B", "type", "NORMAL"));
        Long wh = createId("/masters/warehouses",
                Map.of("code", "QFW" + SFX, "name", "조건검증창고", "type", "MAIN"));

        bookMock = book("QFM" + SFX, "조건검증 모의고사", "Q2101A01", "모의고사");
        bookText = book("QFT" + SFX, "조건검증 교재", "Q2101B01", "교재");
        for (Long p : List.of(bookMock, bookText)) {
            post("/stock/inbound", Map.of("processedDate", DATE, "supplierClientId", supplier,
                    "destinationWarehouseId", wh,
                    "items", List.of(Map.of("productId", p, "unitCost", 1000, "qty", 1000))));
        }

        // A: 모의고사 10 (학교 S1) · 교재 20 (학교 S2)   B: 모의고사 30 (학교 S1)
        sale(partnerA, wh, bookMock, 10, "QFS1");
        sale(partnerA, wh, bookText, 20, "QFS2");
        sale(partnerB, wh, bookMock, 30, "QFS1");
    }

    private Long book(String code, String name, String catCode, String division) {
        Map<String, Object> b = new HashMap<>();
        b.put("code", code);
        b.put("name", name);
        b.put("contentType", "SELF");
        b.put("price", 10000);
        b.put("supplyRate", 70);
        b.put("catCode", catCode);
        b.put("catName", name + "분류");
        b.put("salesDivision", division);
        return createId("/masters/products", b);
    }

    private void sale(Long partner, Long wh, Long product, int qty, String school) {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", product);
        item.put("shipmentType", "NORMAL_SHIP");
        item.put("unitPrice", 10000);
        item.put("supplyRate", 70);
        item.put("qty", qty);
        item.put("schoolCode", school);
        JsonNode r = post("/sales/entries", Map.of(
                "salesDate", DATE, "partnerId", partner, "warehouseId", wh,
                "items", List.of(item)));
        assertThat(r.path("success").asBoolean()).as("매출등록: %s", r).isTrue();
    }

    private JsonNode rows(String query) {
        return data(get("/sales?size=200&" + PERIOD + query)).path("content");
    }

    @Test
    @DisplayName("★세부구분·도서 조건이 서버에서 걸린다")
    void 세부구분_도서() {
        assertThat(rows("&partnerIds=" + partnerA + "," + partnerB)).as("전체 3건").hasSize(3);
        assertThat(rows("&partnerIds=" + partnerA + "," + partnerB + "&divisions=교재"))
                .as("교재만").hasSize(1);
        assertThat(rows("&partnerIds=" + partnerA + "," + partnerB + "&productIds=" + bookMock))
                .as("모의고사 도서만").hasSize(2);
    }

    @Test
    @DisplayName("★★거래처×학교 조합 — 'A는 전체 + B는 그 학교만'")
    void 거래처_학교_조합() {
        // 거래처목록 AND 학교목록으로는 표현할 수 없는 조회다.
        JsonNode r = rows("&partnerSchools=" + partnerA + "&partnerSchools=" + partnerB + ":QFS1");

        assertThat(r).as("A 두 건 + B의 QFS1 한 건").hasSize(3);

        // 반대로 B의 다른 학교만 고르면 A 전체 + (없음) = 2건이어야 한다.
        assertThat(rows("&partnerSchools=" + partnerA + "&partnerSchools=" + partnerB + ":없는학교"))
                .as("B 쪽은 걸러져 A 두 건만").hasSize(2);
    }

    @Test
    @DisplayName("★두 축 정렬 — 보낸 순서가 우선순위다")
    void 다중_정렬() {
        JsonNode r = data(get("/sales?size=200&" + PERIOD
                + "&partnerIds=" + partnerA + "," + partnerB
                + "&sort=partnerCode,asc&sort=qty,desc")).path("content");

        assertThat(r).hasSize(3);
        // A(코드가 앞) 가 먼저 오고, A 안에서는 수량 내림차순(20 → 10)
        assertThat(r.get(0).path("partnerId").asLong()).isEqualTo(partnerA);
        assertThat(r.get(0).path("qty").asInt()).isEqualTo(20);
        assertThat(r.get(1).path("qty").asInt()).isEqualTo(10);
        assertThat(r.get(2).path("partnerId").asLong()).isEqualTo(partnerB);
    }

    @Test
    @DisplayName("★★정렬 하나짜리(sort=항목,방향)도 된다 — 프론트 실측 A-2(400)")
    void 단일_정렬() {
        // ‼️Spring 이 `sort=salesDate,desc` 를 **List 두 개**로 쪼갠다 — ["salesDate","desc"].
        //   그래서 'desc' 를 정렬 항목으로 보고 400 이 났다. 표준 형식이라 엑셀·외부 호출도 같이 막혔다.
        JsonNode r = get("/sales?size=200&" + PERIOD + "&partnerIds=" + partnerA
                + "&sort=salesDate,desc");
        assertThat(r.path("success").asBoolean()).as("단일 정렬: %s", r).isTrue();

        // 둘로 쪼개져 들어와도 같은 뜻이어야 한다(같은 요청의 다른 표기다).
        assertThat(get("/sales?size=200&" + PERIOD + "&partnerIds=" + partnerA
                + "&sort=salesDate&sort=desc").path("success").asBoolean()).isTrue();

        assertThat(get("/disposals?fromDate=2101-01-01&toDate=2101-12-31&sort=id,asc")
                .path("success").asBoolean()).as("폐기 목록도 같다").isTrue();
    }

    @Test
    @DisplayName("모르는 정렬 항목은 400 — 조용히 무시하면 정렬 안 된 표를 정렬된 줄 안다")
    void 모르는_정렬() {
        JsonNode r = get("/sales?" + PERIOD + "&sort=없는컬럼,asc");

        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").path("message").asText()).contains("정렬할 수 없는");
    }

    @Test
    @DisplayName("★수불부 분류 조건")
    void 수불부_분류() {
        JsonNode all = data(get("/stock/ledger?size=200&" + PERIOD + "&keyword=" + "QF"))
                .path("content");
        assertThat(all).as("두 도서 모두 잡힌다").isNotEmpty();

        // ‼️수불부 응답에는 분류코드 칸이 없다 — 걸러졌는지는 남은 상품으로 본다.
        //   (교재 분류 Q2101B01 에 속한 도서는 bookText 하나뿐이다)
        JsonNode filtered = data(get("/stock/ledger?size=200&" + PERIOD + "&catCodes=Q2101B01"))
                .path("content");
        assertThat(filtered).as("그 분류 도서만 남는다").isNotEmpty();
        for (JsonNode row : filtered) {
            assertThat(row.path("productId").asLong()).isEqualTo(bookText);
        }
    }

    @Test
    @DisplayName("★순매출 — 거래처·세부구분·분류·도서 조건")
    void 순매출_조건() {
        JsonNode both = data(get("/sales/net-summary?" + PERIOD
                + "&partnerIds=" + partnerA + "," + partnerB)).path("rows");
        assertThat(both).as("상품 2종").hasSize(2);

        JsonNode onlyText = data(get("/sales/net-summary?" + PERIOD + "&divisions=교재")).path("rows");
        assertThat(onlyText).hasSize(1);
        assertThat(onlyText.get(0).path("productId").asLong()).isEqualTo(bookText);

        assertThat(data(get("/sales/net-summary?" + PERIOD + "&catCodes=Q2101A01")).path("rows"))
                .hasSize(1);
        assertThat(data(get("/sales/net-summary?" + PERIOD + "&productIds=" + bookText)).path("rows"))
                .hasSize(1);
    }

    @Test
    @DisplayName("★매출액정리 거래분류 다중선택")
    void 매출액정리_거래분류() {
        JsonNode sale = data(get("/sales/summary?" + PERIOD + "&partnerId=" + partnerA
                + "&tradeClasses=SALES")).path("rows");
        assertThat(sale).as("A의 매출 2종").hasSize(2);

        // 입고·폐기는 매출 원장에 없다 — 그것만 고르면 한 건도 안 나오는 것이 맞다.
        JsonNode none = data(get("/sales/summary?" + PERIOD + "&partnerId=" + partnerA
                + "&tradeClasses=INBOUND")).path("rows");
        assertThat(none).as("매출 원장에 없는 분류만 고르면 빈 결과").isEmpty();
    }

    @Test
    @DisplayName("★위탁 미결 — 거래처를 비우면 전체, 줄마다 거래처가 보인다")
    void 위탁미결_전체() {
        JsonNode d = data(get("/consignment/pending"));

        assertThat(d.path("success").isMissingNode()).isTrue();
        assertThat(d.hasNonNull("partnerId")).as("전체 조회면 머리의 거래처는 비운다").isFalse();
        for (JsonNode line : d.path("items")) {
            assertThat(line.hasNonNull("partnerId")).as("줄마다 거래처가 실린다").isTrue();
        }
    }
}
