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
 * 물류 작업요청서·작업결과 회귀 고정. 레거시 sendData 흐름을 그대로 옮긴 부분이라
 * "발송 단위를 어떻게 묶는가"가 어긋나면 물류 화면 전체가 틀어진다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("물류 작업요청서·작업결과")
class WorkIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-WK" + (System.nanoTime() % 1_000_000L);

    private Long partner;
    private Long book;
    private Long exam;

    @BeforeAll
    void seed() {
        token();
        Long sup = createId("/masters/clients", Map.of("code", "WKS" + SFX, "name", "인쇄", "type", "NORMAL"));
        partner = createId("/masters/clients", Map.of("code", "WKP" + SFX, "name", "하람도서", "type", "NORMAL"));
        Long wh = createId("/masters/warehouses", Map.of("code", "WKW" + SFX, "name", "물류", "type", "MAIN"));
        book = createId("/masters/products", Map.of("code", "WKA" + SFX, "name", "국어교재",
                "contentType", "SELF", "price", 10000, "supplyRate", 75, "salesDivision", "교재"));
        exam = createId("/masters/products", Map.of("code", "WKB" + SFX, "name", "IC모의고사",
                "contentType", "SELF", "price", 5000, "supplyRate", 75, "salesDivision", "IC"));
        post("/stock/inbound", Map.of("processedDate", "2027-04-01", "supplierClientId", sup,
                "destinationWarehouseId", wh,
                "items", List.of(Map.of("productId", book, "unitCost", 3000, "qty", 500),
                        Map.of("productId", exam, "unitCost", 1000, "qty", 500))));

        // 같은 날·같은 학교로 두 분류를 등록 → 발송은 분류별로 나뉜다
        post("/sales/entries", Map.of("salesDate", "2027-04-10", "partnerId", partner, "warehouseId", wh,
                "items", List.of(
                        Map.of("productId", book, "shipmentType", "NORMAL_SHIP", "qty", 40,
                                "schoolCode", "10001", "schoolName", "강남대성학원"),
                        Map.of("productId", exam, "shipmentType", "NORMAL_SHIP", "qty", 20,
                                "schoolCode", "10001", "schoolName", "강남대성학원"))));
        // 같은 분류로 한 번 더 → 발송 건은 늘지 않고 수량만 합쳐진다
        post("/sales/entries", Map.of("salesDate", "2027-04-10", "partnerId", partner, "warehouseId", wh,
                "items", List.of(Map.of("productId", book, "shipmentType", "NORMAL_SHIP", "qty", 10,
                        "schoolCode", "10001", "schoolName", "강남대성학원"))));
    }

    private static final String RANGE = "?fromDate=2027-04-01&toDate=2027-04-30";

    @Test
    @DisplayName("매출등록이 발송 건을 만들고, 같은 단위면 하나로 묶인다")
    void 발송건_생성단위() {
        JsonNode orders = data(get("/logistics/work-orders" + RANGE + "&partnerId=" + partner));
        // 물류는 품목이 아니라 상자 단위로 일한다 — 두 번 등록해도 발송은 분류당 하나다.
        assertThat(orders).hasSize(2);

        JsonNode textbook = null;
        for (JsonNode o : orders) {
            if ("교재".equals(o.path("tradeClass").asText())) {
                textbook = o;
            }
        }
        assertThat(textbook).isNotNull();
        assertThat(textbook.path("totalQty").asLong()).as("40+10이 한 건에 합쳐진다").isEqualTo(50);
        assertThat(textbook.path("lines")).hasSize(1);
        assertThat(textbook.path("lines").get(0).path("amount").asLong()).isEqualTo(375_000);
    }

    @Test
    @DisplayName("출력 처리는 최초 시각만 남긴다 — 재출력해도 덮지 않는다")
    void 출력처리() {
        Long id = data(get("/logistics/work-orders" + RANGE + "&partnerId=" + partner)).get(0).path("id").asLong();

        assertThat(data(post("/logistics/work-orders/" + id + "/print", null)).asBoolean())
                .as("처음 출력").isTrue();
        assertThat(data(post("/logistics/work-orders/" + id + "/print", null)).asBoolean())
                .as("재출력은 기록을 바꾸지 않는다").isFalse();

        // 미출력 필터에서 빠져야 한다(레거시의 '출력 안 된 건만' 필터)
        for (JsonNode o : data(get("/logistics/work-orders" + RANGE + "&partnerId=" + partner + "&printed=false"))) {
            assertThat(o.path("id").asLong()).isNotEqualTo(id);
        }
    }

    @Test
    @DisplayName("발송정보는 지정한 항목만 바뀐다 — 박스만 고치다 발송일이 지워지면 안 된다")
    void 발송정보_부분입력() {
        Long id = data(get("/logistics/work-orders" + RANGE + "&partnerId=" + partner)).get(0).path("id").asLong();

        put("/logistics/work-orders/" + id + "/shipping", Map.of("boxCount", 3, "sendMemo", "착불"));
        put("/logistics/work-orders/" + id + "/shipping", Map.of("sentDate", "2027-04-12"));

        JsonNode row = null;
        for (JsonNode r : data(get("/logistics/work-results" + RANGE + "&partnerId=" + partner))) {
            if (r.path("id").asLong() == id) {
                row = r;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.path("boxCount").asInt()).as("먼저 넣은 박스 수가 남아 있다").isEqualTo(3);
        assertThat(row.path("sendMemo").asText()).isEqualTo("착불");
        assertThat(row.path("sentDate").asText()).isEqualTo("2027-04-12");
        // 레거시에 completeDate를 쓰는 코드가 없다 — 우리도 임의로 만들지 않았다.
        assertThat(row.path("completed").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("작업결과는 상품군별 수량을 나눠 보여준다(교재·IC)")
    void 작업결과_수량분해() {
        long textbookQty = 0;
        long examQty = 0;
        for (JsonNode r : data(get("/logistics/work-results" + RANGE + "&partnerId=" + partner))) {
            textbookQty += r.path("quantities").path("교재").asLong();
            examQty += r.path("quantities").path("IC").asLong();
        }
        assertThat(textbookQty).isEqualTo(50);
        assertThat(examQty).isEqualTo(20);
    }
}
