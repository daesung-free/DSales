package com.daesung.sales.verification;

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
 * 검수 시나리오 S-3 — 위탁 미결 부분정산.
 * 근거: 개발문서 2탭 13.0 / 1탭 18 완료조건 "13p 분할정산 시나리오 <b>5종 이상</b> 인수테스트 통과".
 *
 * <p>통과 기준 3가지(누적 정확 · 출고수량 초과 정산 차단 · 원본출고번호 역추적)를
 * 5개 시나리오로 나눠 검증한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("검수 시나리오 S-3(위탁 미결 부분정산 5종)")
class ConsignSettlementScenarioTest extends IntegrationTestSupport {

    /**
     * 실행별 고유 접두어. Gradle 테스트 워커 JVM이 빌드 간 재사용되면 static 컨테이너의 데이터가
     * 남아, 고정 코드를 쓰면 두 번째 실행에서 "이미 존재하는 코드"로 깨진다.
     */
    private static final String SFX = "-" + (System.nanoTime() % 1_000_000L);

    private Long supplier;
    private Long partner;
    private Long mainWh;
    private Long consignWh;

    @BeforeAll
    void seed() {
        token();
        supplier = createId("/masters/clients", Map.of("code", "CS-SUP" + SFX, "name", "정산인쇄소", "type", "NORMAL"));
        partner = createId("/masters/clients", Map.of("code", "CS-CUST" + SFX, "name", "정산특약점", "type", "CONSIGN"));
        mainWh = createId("/masters/warehouses", Map.of("code", "CS-M" + SFX, "name", "정산물류창고", "type", "MAIN"));
        consignWh = createId("/masters/warehouses",
                Map.of("code", "CS-C" + SFX, "name", "정산위탁창고", "type", "CONSIGN", "ownerClientId", partner));
    }

    @Test
    @DisplayName("S-3-1 전량 정산 — 100 출고 → 100 정산, 미결 소진(CLOSED)")
    void 시나리오1_전량정산() {
        JsonNode pending = ship("CS-P1", 100);
        long outId = pending.path("consignmentOutId").asLong();

        settle(outId, 100, "2026-07-01");

        JsonNode after = pendingLine("CS-P1");
        assertThat(after).as("잔여 0이면 미결 목록에서 빠져야 함").isNull();
    }

    @Test
    @DisplayName("S-3-2 분할 정산 — 100을 30+70으로 나눠 정산, 누적이 정확히 쌓인다")
    void 시나리오2_분할누적() {
        JsonNode pending = ship("CS-P2", 100);
        long outId = pending.path("consignmentOutId").asLong();

        settle(outId, 30, "2026-07-02");
        JsonNode mid = pendingLine("CS-P2");
        assertThat(mid.path("settledQty").asInt()).as("1차 누적").isEqualTo(30);
        assertThat(mid.path("remainingQty").asInt()).as("잔여").isEqualTo(70);
        assertThat(mid.path("status").asText()).isEqualTo("PARTIAL");
        assertThat(mid.path("totalQty").asInt())
                .as("불변식: 총출고 = 기정산 + 미결잔여")
                .isEqualTo(mid.path("settledQty").asInt() + mid.path("remainingQty").asInt());

        settle(outId, 70, "2026-07-03");
        assertThat(pendingLine("CS-P2")).as("전량 소진 후 미결 없음").isNull();
    }

    @Test
    @DisplayName("S-3-3 초과 정산 차단 — 잔여 20인데 50 정산 시도 → 거부, 장부 불변")
    void 시나리오3_초과정산_차단() {
        JsonNode pending = ship("CS-P3", 100);
        long outId = pending.path("consignmentOutId").asLong();
        settle(outId, 80, "2026-07-04");

        JsonNode before = pendingLine("CS-P3");
        assertThat(before.path("remainingQty").asInt()).isEqualTo(20);

        JsonNode over = post("/consignment/settle", Map.of(
                "salesDate", "2026-07-05",
                "settlements", List.of(Map.of("consignmentOutId", outId, "settleQty", 50,
                        "unitPrice", 20000, "supplyRate", 75))));
        assertThat(over.path("success").asBoolean()).as("초과 정산은 거부돼야 함: %s", over).isFalse();
        assertThat(over.path("error").path("code").asText()).isEqualTo("OVER_SETTLEMENT");

        JsonNode after = pendingLine("CS-P3");
        assertThat(after.path("settledQty").asInt()).as("거부 후 누적 불변").isEqualTo(80);
        assertThat(after.path("remainingQty").asInt()).as("거부 후 잔여 불변").isEqualTo(20);
    }

    @Test
    @DisplayName("S-3-4 원본출고번호 역추적 — 정산 건에서 OUT- 번호로 원 출고를 되짚을 수 있다")
    void 시나리오4_원본출고번호_역추적() {
        JsonNode pending = ship("CS-P4", 60);
        String outNo = pending.path("sourceOutNo").asText();
        assertThat(outNo).as("위탁출고번호 채번").startsWith("OUT-");

        settle(pending.path("consignmentOutId").asLong(), 25, "2026-07-06");

        JsonNode stmt = data(get("/consignment/settlement-statement"
                + "?startDate=2026-07-01&endDate=2026-07-31&partnerId=" + partner));
        boolean found = false;
        for (JsonNode r : stmt.path("rows")) {
            if (outNo.equals(r.path("sourceOutNo").asText())) {
                found = true;
                assertThat(r.path("settleQty").asInt()).as("정산수량").isEqualTo(25);
                assertThat(r.path("totalQty").asInt()).as("원 출고수량 역추적").isEqualTo(60);
                assertThat(r.path("remainingQty").asInt()).as("미결 잔여").isEqualTo(35);
            }
        }
        assertThat(found).as("정산내역서에서 원본출고번호 %s로 역추적 가능해야 함: %s", outNo, stmt).isTrue();
    }

    @Test
    @DisplayName("S-3-5 정산 후 매출취소 — 미결원장이 정산 전으로 복원된다")
    void 시나리오5_정산취소_미결복원() {
        JsonNode pending = ship("CS-P5", 100);
        long outId = pending.path("consignmentOutId").asLong();
        settle(outId, 40, "2026-08-10");

        JsonNode mid = pendingLine("CS-P5");
        assertThat(mid.path("settledQty").asInt()).isEqualTo(40);

        long saleId = data(get("/sales?startDate=2026-08-01&endDate=2026-08-31&partnerId=" + partner))
                .path("content").get(0).path("id").asLong();
        JsonNode cancel = post("/sales/" + saleId + "/cancel", Map.of());
        assertThat(cancel.path("success").asBoolean()).as("취소: %s", cancel).isTrue();

        JsonNode after = pendingLine("CS-P5");
        assertThat(after.path("settledQty").asInt()).as("정산 전으로 복원").isZero();
        assertThat(after.path("remainingQty").asInt()).as("잔여 복원").isEqualTo(100);
    }

    // ── helpers ──

    /** 상품 생성 → 입고 → 위탁출고. 생성된 미결 라인을 반환. */
    private JsonNode ship(String code, int qty) {
        Long p = createId("/masters/products",
                Map.of("code", code + SFX, "name", code, "contentType", "SELF", "price", 20000));
        post("/stock/inbound", Map.of(
                "processedDate", "2026-06-01", "supplierClientId", supplier, "destinationWarehouseId", mainWh,
                "items", List.of(Map.of("productId", p, "unitCost", 3000, "qty", qty))));
        JsonNode out = post("/consignment/out", Map.of(
                "processedDate", "2026-06-05", "partnerId", partner,
                "fromWarehouseId", mainWh, "toWarehouseId", consignWh,
                "items", List.of(Map.of("productId", p, "qty", qty))));
        assertThat(out.path("success").asBoolean()).as("위탁출고: %s", out).isTrue();
        JsonNode line = pendingLine(code);
        assertThat(line).as("미결 생성 확인").isNotNull();
        return line;
    }

    private void settle(long outId, int qty, String salesDate) {
        JsonNode r = post("/consignment/settle", Map.of(
                "salesDate", salesDate,
                "settlements", List.of(Map.of("consignmentOutId", outId, "settleQty", qty,
                        "unitPrice", 20000, "supplyRate", 75))));
        assertThat(r.path("success").asBoolean()).as("정산 %d건: %s", qty, r).isTrue();
    }

    /** 거래처 미결 목록에서 상품코드로 라인 찾기. 없으면 null(=소진). */
    private JsonNode pendingLine(String productCode) {
        JsonNode lines = data(get("/consignment/pending?partnerId=" + partner)).path("items");
        for (JsonNode l : lines) {
            if ((productCode + SFX).equals(l.path("productCode").asText())) {
                return l;
            }
        }
        return null;
    }
}
