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
 * 응시현황(기간별) 조회구분·학년 다중선택. 근거: 테스트 피드백 1차(2026-09-17) —
 * "레거시 매출프로그램 메뉴의 화면 및 데이터를 유지하기로 했는데 조회 구분이 상이함".
 *
 * <p>레거시({@code 고사별처리인원.vb})는 <b>지역별/특약점별/학교별</b> 라디오와
 * <b>학년 다중 체크</b>를 갖고 있었다. 우리는 학년 단건·거래처만 받고 있었다.
 *
 * <p>★<b>레거시의 분류코드 해독은 옮기지 않았다.</b> 레거시는 3,422줄에 걸쳐 연도마다
 * {@code catCode='M{2}A' and bookCode='11' → 1학년 4월} 식으로 코드 글자를 뒤진다.
 * 학년·회차 필드가 없어서 그랬던 것이고 <b>우리는 그 필드를 갖고 있다.</b>
 * 그래서 연도별 하드코딩 없이 같은 숫자를 낼 수 있다.
 *
 * <p>‼️다만 <b>더프 판별만은 분류코드 규칙을 그대로 쓴다</b>({@code M} + {@code A/B/C} 계열).
 * 대분류(MOCK_EXAM)로 바꿔 봤더니 더프가 아닌 모의고사 계열(M…D)까지 섞여 들어왔다 —
 * {@code AttendancePeriodIntegrationTest}가 그 케이스를 넣어 두고 있어 바로 드러났다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("응시현황 조회구분·학년 다중선택")
class AttendanceGroupByIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-AG" + (System.nanoTime() % 1_000_000L);
    private static final int YEAR = 2099;
    private static final String RANGE = "?fromDate=" + YEAR + "-01-01&toDate=" + YEAR + "-12-31";

    @BeforeAll
    void seed() {
        token();
        Long sup = createId("/masters/clients",
                Map.of("code", "AGS" + SFX, "name", "인쇄소", "type", "NORMAL"));
        Long wh = createId("/masters/warehouses",
                Map.of("code", "AGW" + SFX, "name", "응시창고", "type", "MAIN"));

        // 같은 지역에 거래처 둘 — 지역 소계가 합쳐지는지 보려면 필요하다.
        Long p1 = createId("/masters/clients", Map.of(
                "code", "AGP1" + SFX, "name", "서울특약점A", "type", "NORMAL", "region", "서울"));
        Long p2 = createId("/masters/clients", Map.of(
                "code", "AGP2" + SFX, "name", "서울특약점B", "type", "NORMAL", "region", "서울"));

        // ★모의고사로 판별되려면 세부구분이 모의고사여야 한다(대분류 MOCK_EXAM).
        Long g1 = mock("AG1" + SFX, "모의고사1학년", "1");
        Long g3 = mock("AG3" + SFX, "모의고사3학년", "3");

        post("/stock/inbound", Map.of("processedDate", YEAR + "-01-05", "supplierClientId", sup,
                "destinationWarehouseId", wh,
                "items", List.of(Map.of("productId", g1, "unitCost", 1000, "qty", 1000),
                        Map.of("productId", g3, "unitCost", 1000, "qty", 1000))));

        sell(wh, p1, g1, "AG-S1" + SFX, "가고", 10);
        sell(wh, p1, g3, "AG-S2" + SFX, "나고", 20);
        sell(wh, p2, g3, "AG-S3" + SFX, "다고", 30);
    }

    private Long mock(String code, String name, String grade) {
        Map<String, Object> b = new HashMap<>();
        b.put("code", code);
        b.put("name", name);
        b.put("contentType", "SELF");
        b.put("price", 10000);
        b.put("supplyRate", 70);
        // ‼️더프 판별은 분류코드 M+A/B/C 계열이다(레거시 규칙). 대분류로는 못 가른다 —
        //   더프가 아닌 모의고사 계열(M…D)이 섞이기 때문이다.
        b.put("catCode", "M" + YEAR + "A");
        b.put("catName", "응시분류");
        b.put("grade", grade);
        b.put("salesDivision", "모의고사");   // 대분류 MOCK_EXAM 으로 매핑된다
        return createId("/masters/products", b);
    }

    private void sell(Long wh, Long partner, Long book, String schoolCode, String schoolName, int qty) {
        JsonNode r = post("/sales/entries", Map.of(
                "salesDate", YEAR + "-03-10", "partnerId", partner, "warehouseId", wh,
                "items", List.of(Map.of("productId", book, "shipmentType", "NORMAL_SHIP",
                        "unitPrice", 10000, "supplyRate", 70, "qty", qty,
                        "schoolCode", schoolCode, "schoolName", schoolName))));
        assertThat(r.path("success").asBoolean()).as("%s", r).isTrue();
    }

    private JsonNode rows(String query) {
        return data(get("/sales/attendance-period" + RANGE + query)).path("rows");
    }

    private long totalOf(JsonNode rows, String rowType) {
        long sum = 0;
        for (JsonNode r : rows) {
            if (rowType.equals(r.path("rowType").asText())) {
                sum += r.path("gradedTotal").asLong() + r.path("ungradedTotal").asLong();
            }
        }
        return sum;
    }

    @Test
    @DisplayName("★학교별(기본) — 학교 줄이 펼쳐진다")
    void 학교별() {
        JsonNode r = rows("&groupBy=SCHOOL");

        assertThat(totalOf(r, "SCHOOL")).as("10+20+30").isEqualTo(60);
    }

    @Test
    @DisplayName("★특약점별 — 학교 줄은 접히고 거래처 소계만 남는다")
    void 특약점별() {
        JsonNode r = rows("&groupBy=PARTNER");

        assertThat(totalOf(r, "SCHOOL")).as("학교 줄은 접힌다").isZero();
        assertThat(totalOf(r, "PARTNER_SUBTOTAL")).as("거래처 소계 합은 그대로").isEqualTo(60);
    }

    @Test
    @DisplayName("★지역별 — 거래처 소계까지 접히고 지역만 남는다")
    void 지역별() {
        JsonNode r = rows("&groupBy=REGION");

        assertThat(totalOf(r, "SCHOOL")).isZero();
        assertThat(totalOf(r, "PARTNER_SUBTOTAL")).as("거래처 소계도 접힌다").isZero();
        assertThat(totalOf(r, "REGION_SUBTOTAL")).as("서울 한 줄로 60").isEqualTo(60);
    }

    @Test
    @DisplayName("★학년 다중선택 — 1·3학년을 함께 고른다")
    void 학년_다중() {
        assertThat(totalOf(rows("&groupBy=SCHOOL&grades=1"), "SCHOOL")).as("1학년만").isEqualTo(10);
        assertThat(totalOf(rows("&groupBy=SCHOOL&grades=3"), "SCHOOL")).as("3학년만").isEqualTo(50);
        assertThat(totalOf(rows("&groupBy=SCHOOL&grades=1,3"), "SCHOOL")).as("둘 다").isEqualTo(60);
    }

    @Test
    @DisplayName("모르는 조회구분은 400 — 행 수가 수십 배 달라지는데 오류가 안 나면 안 된다")
    void 모르는_조회구분() {
        JsonNode r = get("/sales/attendance-period" + RANGE + "&groupBy=학교");

        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").path("message").asText()).contains("조회구분", "REGION");
    }
}
