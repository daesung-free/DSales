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
 * 제품수불부 무상 4칸(IC학생용·IC·M+·기타) — 레거시 재현.
 *
 * <p>근거: {@code 제품수불부.vb:118~121}. 무상 안에서 {@code part} × 대분류로 갈린다.
 * <pre>
 *   IC학생용 = part 학생용 × 대분류 IC
 *   IC       = part 교사용 × 대분류 IC
 *   M+       = part M+
 *   기타     = 나머지 (‼️IC+ 가 여기로 떨어진다 — 레거시가 M+만 떼어냈다)
 * </pre>
 *
 * <p>★<b>기존 free/teacher 는 손대지 않았다.</b> 레거시는 '교사용'을 교재분만으로 좁히지만,
 * 그렇게 바꾸면 순매출조회·외상매출현황의 교사용과 숫자가 갈린다 — 2026-09-11에 겪은
 * 3,029/3,006이 그 사고다. 그래서 4칸은 <b>다시 쪼갠 보조 칸</b>으로 얹었고,
 * 이 테스트가 "기존 칸이 안 바뀐다"까지 함께 고정한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
@DisplayName("수불부 무상 4칸(레거시 재현)")
class FreeDetailIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-FD" + (System.nanoTime() % 1_000_000L);
    private static final int YEAR = 2088;
    private static final String RANGE = "?fromDate=" + YEAR + "-01-01&toDate=" + YEAR + "-12-31";

    private Long wh;
    private Long partner;
    private Long icBook;
    private Long textBook;
    private String icCode;
    private String textCode;

    @BeforeAll
    void seed() {
        token();
        Long sup = createId("/masters/clients",
                Map.of("code", "FDS" + SFX, "name", "인쇄소", "type", "NORMAL"));
        partner = createId("/masters/clients",
                Map.of("code", "FDP" + SFX, "name", "무상거래처", "type", "NORMAL"));
        wh = createId("/masters/warehouses",
                Map.of("code", "FDW" + SFX, "name", "무상창고", "type", "MAIN"));

        icCode = "FDIC" + SFX;
        textCode = "FDTX" + SFX;
        icBook = book(icCode, "IC도서", "IC");   // 세부구분 코드는 한글이다(V41)
        textBook = book(textCode, "교재도서", "교재");

        post("/stock/inbound", Map.of("processedDate", YEAR + "-01-05", "supplierClientId", sup,
                "destinationWarehouseId", wh,
                "items", List.of(Map.of("productId", icBook, "unitCost", 1000, "qty", 1000),
                        Map.of("productId", textBook, "unitCost", 1000, "qty", 1000))));
    }

    private Long book(String code, String name, String division) {
        Map<String, Object> b = new HashMap<>();
        b.put("code", code);
        b.put("name", name);
        b.put("contentType", "SELF");
        b.put("price", 10000);
        b.put("supplyRate", 70);
        b.put("catCode", "F" + YEAR + "A");
        b.put("catName", "무상분류");
        b.put("salesDivision", division);
        return createId("/masters/products", b);
    }

    private JsonNode ship(Long product, String shipmentType, String part, int qty) {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", product);
        item.put("shipmentType", shipmentType);
        item.put("unitPrice", 10000);
        item.put("supplyRate", 0);
        item.put("qty", qty);
        if (part != null) {
            item.put("part", part);
        }
        return post("/sales/entries", Map.of(
                "salesDate", YEAR + "-02-10", "partnerId", partner, "warehouseId", wh,
                "items", List.of(item)));
    }

    private JsonNode row(String code) {
        for (JsonNode r : data(get("/stock/ledger" + RANGE + "&warehouseId=" + wh
                + "&keyword=" + code + "&size=50")).path("content")) {
            if (code.equals(r.path("productCode").asText())) {
                return r;
            }
        }
        throw new IllegalStateException("수불부 행 없음: " + code);
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("★IC 도서 — 학생용/교사용/M+/IC+ 가 네 칸으로 갈린다")
    void IC_네칸() {
        assertThat(ship(icBook, "GIFT", "학생용", 10).path("success").asBoolean()).isTrue();
        assertThat(ship(icBook, "TEACHER_USE", "교사용", 7).path("success").asBoolean()).isTrue();
        assertThat(ship(icBook, "GIFT", "M+", 5).path("success").asBoolean()).isTrue();
        assertThat(ship(icBook, "GIFT", "IC+", 3).path("success").asBoolean()).isTrue();

        JsonNode r = row(icCode);
        assertThat(r.path("freeIcStudent").asLong()).as("학생용 × IC").isEqualTo(-10);
        assertThat(r.path("freeIc").asLong()).as("교사용 × IC").isEqualTo(-7);
        assertThat(r.path("freeMplus").asLong()).as("M+").isEqualTo(-5);
        // ‼️IC+ 는 자기 칸이 없다 — 레거시가 M+만 떼어냈다.
        assertThat(r.path("freeEtc").asLong()).as("IC+ 는 기타로").isEqualTo(-3);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("★교재 교사용은 '기타' — 레거시가 IC 칸을 대분류로 가른다")
    void 교재_교사용은_기타() {
        assertThat(ship(textBook, "TEACHER_USE", "교사용", 20).path("success").asBoolean()).isTrue();

        JsonNode r = row(textCode);
        assertThat(r.path("freeIc").asLong()).as("대분류가 교재라 IC 칸이 아니다").isZero();
        assertThat(r.path("freeEtc").asLong()).isEqualTo(-20);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("★기존 free/teacher 는 그대로다 — 4칸을 얹으면서 뜻이 바뀌면 안 된다")
    void 기존칸_불변() {
        JsonNode ic = row(icCode);
        // 증정 10(학생용) + 5(M+) + 3(IC+) = 18, 교사용 7
        assertThat(ic.path("free").asLong()).as("증정 총계").isEqualTo(-18);
        assertThat(ic.path("teacher").asLong()).as("교사용 총계").isEqualTo(-7);

        // 4칸 합 = free + teacher. 어느 하나라도 빠지면 담당자가 검산할 수 없다.
        long four = ic.path("freeIcStudent").asLong() + ic.path("freeIc").asLong()
                + ic.path("freeMplus").asLong() + ic.path("freeEtc").asLong();
        assertThat(four).isEqualTo(ic.path("free").asLong() + ic.path("teacher").asLong());
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("재고 방정식은 그대로 — 4칸은 보조 칸이라 재고 계산에 끼지 않는다")
    void 재고방정식() {
        JsonNode r = row(icCode);
        long sum = r.path("opening").asLong() + r.path("inbound").asLong()
                + r.path("transfer").asLong() + r.path("bom").asLong()
                + r.path("dispose").asLong() + r.path("sale").asLong()
                + r.path("free").asLong() + r.path("teacher").asLong()
                + r.path("salesReturn").asLong() + r.path("adjust").asLong();

        assertThat(sum).isEqualTo(r.path("closing").asLong());
        assertThat(r.path("reconciled").asBoolean()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("★모르는 구분은 400 — 표기가 갈리면 집계가 조용히 쪼개진다")
    void 모르는_구분() {
        JsonNode r = ship(icBook, "GIFT", "엠플러스", 1);

        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").path("message").asText()).contains("무상 세부구분", "M+");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("전각 + 는 받아 준다 — 한글 입력 상태로 치면 이렇게 들어온다")
    void 전각_플러스() {
        assertThat(ship(icBook, "GIFT", "M＋", 2).path("success").asBoolean()).isTrue();
        assertThat(row(icCode).path("freeMplus").asLong()).as("M+ 로 합쳐져야").isEqualTo(-7);
    }
}
