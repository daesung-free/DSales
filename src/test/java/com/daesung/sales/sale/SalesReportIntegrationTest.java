package com.daesung.sales.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 매출 리포트 6종 통합테스트(수동 E2E를 자동 회귀 검증기로 고정).
 * 완전 빈 DB(Testcontainers)에 시드 → 각 리포트 호출 → 검증한 숫자를 assert.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("매출 리포트 통합테스트")
class SalesReportIntegrationTest extends IntegrationTestSupport {

    private Long partnerId;
    private Long pA011;
    private Long pA012;
    private Long pA021;
    private Long pB011;

    @BeforeAll
    void seed() {
        token();
        // 창고(MAIN)
        Long whId = createId("/masters/warehouses",
                Map.of("code", "WH-MAIN", "name", "메인창고", "type", "MAIN"));
        // 거래처(공급·판매 겸용)
        partnerId = createId("/masters/clients",
                Map.of("code", "CUST-1", "name", "테스트거래처", "type", "NORMAL"));
        // 상품(catCode): 대분류 A(A01×2, A02), B(B01)
        pA011 = product("BK-A011", "A01", "국어모의");
        pA012 = product("BK-A012", "A01", "국어모의");
        pA021 = product("BK-A021", "A02", "수학모의");
        pB011 = product("BK-B011", "B01", "교재");
        // 입고 1000씩 @원가 3000
        for (Long pid : List.of(pA011, pA012, pA021, pB011)) {
            inbound(whId, pid);
        }
        // 당해(2026-06) 매출
        sale("2026-06-15", whId, pA011, "NORMAL_SHIP", 50, 10);
        sale("2026-06-15", whId, pA012, "NORMAL_SHIP", 50, 20);
        sale("2026-06-15", whId, pA021, "NORMAL_SHIP", 50, 5);
        sale("2026-06-15", whId, pB011, "NORMAL_SHIP", 50, 4);
        sale("2026-06-15", whId, pA011, "TEACHER_USE", 0, 3);  // 무가(교사용)
        sale("2026-06-20", whId, pA011, "RETURN", 50, 2);      // 반품
        // 전년(2025-06) 매출
        sale("2025-06-15", whId, pA011, "NORMAL_SHIP", 50, 8);
        sale("2025-06-15", whId, pA012, "NORMAL_SHIP", 50, 25);
    }

    private Long product(String code, String catCode, String catName) {
        return createId("/masters/products", Map.of(
                "code", code, "name", code + " 도서", "contentType", "SELF", "set", false,
                "price", 10000, "taxFree", false, "grade", "고3",
                "catCode", catCode, "catName", catName, "useYn", true));
    }

    private void inbound(Long whId, Long productId) {
        post("/stock/inbound", Map.of(
                "processedDate", "2026-06-01", "supplierClientId", partnerId, "destinationWarehouseId", whId,
                "items", List.of(Map.of("productId", productId, "unitCost", 3000, "qty", 1000))));
    }

    /** 입고구분 지정 입고(매입입고 필터 검증용). */
    private void inbound(Long whId, Long productId, String inboundType, int unitCost, int qty) {
        post("/stock/inbound", Map.of(
                "processedDate", "2026-06-01", "supplierClientId", partnerId, "destinationWarehouseId", whId,
                "inboundType", inboundType,
                "items", List.of(Map.of("productId", productId, "unitCost", unitCost, "qty", qty))));
    }

    private Long externalProduct(String code) {
        return createId("/masters/products", Map.of(
                "code", code, "name", code + " 외부콘텐츠", "contentType", "EXTERNAL", "set", false,
                "price", 10000, "taxFree", false, "grade", "고3",
                "catCode", "E01", "catName", "이감국어", "useYn", true));
    }

    private void sale(String date, Long whId, Long productId, String shipmentType, int rate, int qty) {
        JsonNode r = post("/sales/entries", Map.of(
                "salesDate", date, "partnerId", partnerId, "warehouseId", whId,
                "items", List.of(Map.of("productId", productId, "shipmentType", shipmentType,
                        "unitPrice", 10000, "supplyRate", rate, "qty", qty))));
        assertThat(r.path("success").asBoolean()).as("매출등록 성공: %s", r).isTrue();
    }

    /** 성적처리 구분 포함 매출 등록(37p 검증용). */
    private void saleP(String date, Long whId, Long productId, String shipmentType, int rate, int qty, String procType) {
        JsonNode r = post("/sales/entries", Map.of(
                "salesDate", date, "partnerId", partnerId, "warehouseId", whId,
                "items", List.of(Map.of("productId", productId, "shipmentType", shipmentType,
                        "unitPrice", 10000, "supplyRate", rate, "qty", qty, "procType", procType))));
        assertThat(r.path("success").asBoolean()).as("매출등록 성공: %s", r).isTrue();
    }

    private JsonNode rowWhere(JsonNode rows, String field, String value) {
        for (JsonNode r : rows) {
            if (value.equals(r.path(field).asText())) {
                return r;
            }
        }
        throw new AssertionError(field + "=" + value + " 행 없음: " + rows);
    }

    @Test
    @DisplayName("매출액명세서 — 분류 rollup + 합계=금액+세액")
    void 매출액명세서() {
        JsonNode d = data(get("/sales/statement?from=2026-06-01&to=2026-06-30&category=SALE"));
        JsonNode rows = d.path("rows");
        JsonNode a01 = rowWhere(rows, "rowType", "CAT_SUBTOTAL");  // 첫 소계 = A01
        assertThat(a01.path("catCode").asText()).isEqualTo("A01");
        assertThat(a01.path("qty").asLong()).isEqualTo(30);        // 10+20
        assertThat(a01.path("amount").asLong()).isEqualTo(150_000);
        JsonNode grand = rowWhere(rows, "rowType", "GRAND_TOTAL");
        assertThat(grand.path("qty").asLong()).isEqualTo(39);      // 10+20+5+4
        assertThat(grand.path("amount").asLong()).isEqualTo(195_000);
        assertThat(grand.path("tax").asLong()).isEqualTo(19_500);  // 과세 10%
        assertThat(grand.path("total").asLong()).isEqualTo(214_500); // 금액+세액
    }

    @Test
    @DisplayName("거래명세서 — 공급자/공급받는자 + 유가/무가 분리")
    void 거래명세서() {
        JsonNode d = data(get("/sales/transaction-statement?partnerId=" + partnerId
                + "&from=2026-06-01&to=2026-06-30"));
        assertThat(d.path("provider").path("name").asText()).isEqualTo("(주)대성테스트");
        assertThat(d.path("receiver").path("code").asText()).isEqualTo("CUST-1");
        assertThat(d.path("pricedLines")).hasSize(4);
        assertThat(d.path("freeLines")).hasSize(1);               // 교사용
        JsonNode t = d.path("totals");
        assertThat(t.path("supplyAmount").asLong()).isEqualTo(195_000);
        assertThat(t.path("total").asLong()).isEqualTo(214_500);
        assertThat(t.path("freeQty").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("과목별매출현황 — 매출/반품/교사용 버킷 + 반품률")
    void 과목별매출현황() {
        JsonNode d = data(get("/sales/category-summary?from=2026-06-01&to=2026-06-30&partnerId=" + partnerId));
        JsonNode a011 = rowWhere(d.path("rows"), "bookCode", "BK-A011");
        assertThat(a011.path("saleQty").asLong()).isEqualTo(10);
        assertThat(a011.path("returnQty").asLong()).isEqualTo(2);
        assertThat(a011.path("netQty").asLong()).isEqualTo(8);
        assertThat(a011.path("teacherQty").asLong()).isEqualTo(3);
        assertThat(a011.path("returnRate").asDouble()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("도서입출고현황 — 매입+매출 이중장부 + 정본 재고")
    void 도서입출고현황() {
        JsonNode d = data(get("/sales/book-inout?from=2026-06-01&to=2026-06-30&catCode=A01"));
        JsonNode a011 = rowWhere(d.path("rows"), "bookCode", "BK-A011");
        assertThat(a011.path("inboundQty").asLong()).isEqualTo(1000);
        assertThat(a011.path("inboundAmount").asLong()).isEqualTo(3_000_000);  // 1000×3000
        assertThat(a011.path("outboundQty").asLong()).isEqualTo(10);
        assertThat(a011.path("returnQty").asLong()).isEqualTo(2);
        assertThat(a011.path("returnRate").asDouble()).isEqualTo(20.0);
        // 정본 재고(as-of 전체 날짜): 1000 −10(2026매출) −3(교사) +2(반품) −8(2025매출) = 981
        assertThat(a011.path("stockQty").asLong()).isEqualTo(981);
        assertThat(a011.path("grossMargin").asLong()).isEqualTo(40_000 - 3_000_000);
    }

    @Test
    @DisplayName("거래처별 매출대비표 — 전년 동기간 비교")
    void 매출대비표() {
        JsonNode d = data(get("/sales/yoy-comparison?from=2026-06-01&to=2026-06-30&groupBy=PARTNER&partnerId="
                + partnerId));
        assertThat(d.path("prevFrom").asText()).isEqualTo("2025-06-01");
        JsonNode row = d.path("rows").get(0);
        assertThat(row.path("curQty").asLong()).isEqualTo(39);
        assertThat(row.path("prevQty").asLong()).isEqualTo(33);   // 8+25
        assertThat(row.path("diffQty").asLong()).isEqualTo(6);
        assertThat(row.path("qtyRatioPct").asDouble()).isCloseTo(118.18, within(0.01));
    }

    @Test
    @DisplayName("매출대비표 BOOK — 전년0이면 비율 null, 감소 처리")
    void 매출대비표_BOOK() {
        JsonNode d = data(get("/sales/yoy-comparison?from=2026-06-01&to=2026-06-30&groupBy=BOOK&partnerId="
                + partnerId));
        JsonNode rows = d.path("rows");
        JsonNode a012 = rowWhere(rows, "bookCode", "BK-A012");
        assertThat(a012.path("diffQty").asLong()).isEqualTo(-5);          // 20 vs 25
        assertThat(a012.path("qtyRatioPct").asDouble()).isEqualTo(80.0);
        JsonNode a021 = rowWhere(rows, "bookCode", "BK-A021");
        assertThat(a021.path("prevQty").asLong()).isEqualTo(0);
        // 전년0 → 비율 null(Jackson NON_NULL이 필드 생략 → 숫자 아님)
        assertThat(a021.path("qtyRatioPct").isNumber()).isFalse();
    }

    @Test
    @DisplayName("월별매출액명세서(37p) — 성적처리/비처리 인원·금액 분리 + 과세·부가세")
    void 월별매출액명세서() {
        Long wh = createId("/masters/warehouses", Map.of("code", "WH-37", "name", "37창고", "type", "MAIN"));
        Long mp = product("MST-M01", "M01", "더프모의");   // 과세(taxFree=false), 대분류 'M'
        inbound(wh, mp);   // 재고 확보(1000)
        // 3월로 격리: 성적처리 30명 + 비처리 20명 (같은 상품)
        saleP("2026-03-10", wh, mp, "NORMAL_SHIP", 100, 30, "GRADED");
        saleP("2026-03-10", wh, mp, "NORMAL_SHIP", 100, 20, "UNGRADED");

        JsonNode rows = data(get("/sales/monthly-statement?year=2026&month=3")).path("rows");
        JsonNode detail = rowWhere(rows, "bookCode", "MST-M01");
        assertThat(detail.path("gradedQty").asLong()).isEqualTo(30);
        assertThat(detail.path("gradedAmount").asLong()).isEqualTo(300_000);   // 10000×100%×30
        assertThat(detail.path("ungradedQty").asLong()).isEqualTo(20);
        assertThat(detail.path("ungradedAmount").asLong()).isEqualTo(200_000);
        assertThat(detail.path("totalQty").asLong()).isEqualTo(50);
        assertThat(detail.path("totalAmount").asLong()).isEqualTo(500_000);
        assertThat(detail.path("taxableAmount").asLong()).isEqualTo(500_000); // 전부 과세
        assertThat(detail.path("vat").asLong()).isEqualTo(50_000);            // 10%
        JsonNode grand = rowWhere(rows, "rowType", "GRAND_TOTAL");
        assertThat(grand.path("gradedQty").asLong()).isEqualTo(30);
        assertThat(grand.path("totalQty").asLong()).isEqualTo(50);
    }

    @Test
    @DisplayName("수익신고 — 거래처×월 순매출/세액(MySQL DATE_FORMAT 회귀 방어)")
    void 수익신고() {
        JsonNode d = data(get("/closing/revenue-report?fromDate=2026-06-01&toDate=2026-06-30"));
        JsonNode total = d.path("total");
        assertThat(total.path("totalNetSupply").asLong()).isEqualTo(185_000);  // 195k−10k
        assertThat(total.path("totalNetTax").asLong()).isEqualTo(18_500);
        JsonNode months = d.path("rows").get(0).path("months");
        assertThat(months.get(0).path("yearMonth").asText()).isEqualTo("202606");
    }

    @Test
    @DisplayName("계산서·세금계산서 월별신고(38p) — 6월 과세=세금계산서 버킷, 순매출/세액")
    void 계산서월별신고() {
        JsonNode d = data(get("/closing/tax-filing?year=2026"));
        JsonNode jun = rowWhere(d.path("rows"), "month", "6");  // 공유 6월 시드(전부 과세)
        // 6월 매출 195,000(50k+100k+25k+20k), 반품 10,000(a011 rate50×2), 무상(교사용)은 제외
        assertThat(jun.path("taxInvoiceSale").asLong()).isEqualTo(195_000);
        assertThat(jun.path("taxInvoiceReturn").asLong()).isEqualTo(10_000);
        assertThat(jun.path("taxInvoiceNet").asLong()).isEqualTo(185_000);   // 195k−10k
        assertThat(jun.path("invoiceSale").asLong()).isEqualTo(0);           // 면세 상품 없음
        assertThat(jun.path("netTotal").asLong()).isEqualTo(185_000);
        assertThat(jun.path("tax").asLong()).isEqualTo(18_500);              // 19,500(매출) − 1,000(반품)
        assertThat(jun.path("taxInvoiceUnissued").asLong()).isEqualTo(0);    // 미발행분 placeholder
        // 12개월 행 항상 존재
        assertThat(d.path("rows")).hasSize(12);
    }

    @Test
    @DisplayName("순매출조회 — 외부콘텐츠 매입액은 매입입고(PURCHASE)만 집계, 정상입고 제외")
    void 순매출_매입입고필터() {
        // 창고·외부콘텐츠 상품 별도 시드(다른 테스트와 격리)
        Long whId = createId("/masters/warehouses", Map.of("code", "WH-EXT", "name", "외부창고", "type", "MAIN"));
        Long ext = externalProduct("EXT-이감01");
        // 정상입고 @5000(매입원가에서 제외돼야 함) + 매입입고 @3000(집계 대상)
        inbound(whId, ext, "NORMAL", 5000, 100);
        inbound(whId, ext, "PURCHASE", 3000, 100);
        // 매출: 공급률 100%, 10부 → 매출액 100,000. 7월로 격리(공유 6월 집계 테스트와 분리)
        sale("2026-07-15", whId, ext, "NORMAL_SHIP", 100, 10);

        JsonNode d = data(get("/sales/net-summary?from=2026-07-01&to=2026-07-31&contentType=EXTERNAL"));
        JsonNode row = rowWhere(d.path("rows"), "productCode", "EXT-이감01");
        assertThat(row.path("contentType").asText()).isEqualTo("EXTERNAL");
        assertThat(row.path("netAmount").asLong()).isEqualTo(100_000);
        // 매입단가 = 매입입고 3000만(정상입고 5000 제외돼 blended 4000이 아님)
        assertThat(row.path("purchaseUnitCost").asLong()).isEqualTo(3000);
        assertThat(row.path("purchaseAmount").asLong()).isEqualTo(30_000);      // 3000×10
        assertThat(row.path("profit").asLong()).isEqualTo(70_000);              // 100,000−30,000
        assertThat(row.path("marginPct").asDouble()).isEqualTo(70.0);
    }
}
