package com.daesung.sales.receivable;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 채권(미수금현황·수금·외상매출장) + 매출 대시보드 통합테스트(백로그 보강).
 * 러닝밸런스(이월+채권발생−수금)·담보비율·목표대비 실적(순매출)·달성률 회귀 고정.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("채권·대시보드 통합테스트")
class ReceivableDashboardIntegrationTest extends IntegrationTestSupport {

    @BeforeAll
    void auth() {
        token();
    }

    @Test
    @DisplayName("미수금현황 — 매출→채권발생, 수금→잔액 차감, 담보비율")
    void 미수금현황_수금() {
        Long supplier = createId("/masters/clients", Map.of("code", "AR-SUP", "name", "입고공급사", "type", "NORMAL"));
        Long wh = createId("/masters/warehouses", Map.of("code", "AR-WH", "name", "채권창고", "type", "MAIN"));
        Long p = createId("/masters/products",
                Map.of("code", "AR-BK", "name", "채권도서", "contentType", "SELF", "price", 10000));
        Long partner = createId("/masters/clients", Map.of("code", "AR-CUST", "name", "채권거래처", "type", "NORMAL"));
        // 담보 200,000 설정
        put("/masters/clients/" + partner, Map.of(
                "name", "채권거래처", "type", "NORMAL", "assureAmount", 200_000, "assureExpiry", "2027-12-31"));
        inbound(wh, supplier, p);

        // 외상매출: 정가10000 공급률100 수량10 → 공급가 100,000
        JsonNode sale = post("/sales/entries", Map.of(
                "salesDate", "2028-06-10", "partnerId", partner, "warehouseId", wh,
                "items", List.of(Map.of("productId", p, "shipmentType", "NORMAL_SHIP",
                        "qty", 10, "unitPrice", 10000, "supplyRate", 100))));
        assertThat(sale.path("success").asBoolean()).as("매출등록: %s", sale).isTrue();

        JsonNode row1 = arRow(partner);
        assertThat(row1.path("saleAmount").asLong()).isEqualTo(100_000);
        long recGen = row1.path("receivableGen").asLong();
        assertThat(recGen).isEqualTo(row1.path("saleAmount").asLong()
                + row1.path("tax").asLong() - row1.path("returnAmount").asLong());   // 채권발생=매출+세액−반품
        assertThat(row1.path("collected").asLong()).isEqualTo(0);
        assertThat(row1.path("balance").asLong()).isEqualTo(row1.path("opening").asLong() + recGen);   // 잔액=이월+채권발생−0
        assertThat(row1.path("assureAmount").asLong()).isEqualTo(200_000);
        assertThat(row1.path("assureRatio").isNull()).isFalse();   // 담보 있으니 비율 산출

        // 수금 50,000(현금) → 잔액 −50,000, 수금 +50,000
        JsonNode coll = post("/closing/collections", Map.of(
                "collDate", "2028-06-20", "partnerId", partner, "collType", "CASH", "collAmt", 50_000));
        assertThat(coll.path("success").asBoolean()).as("수금: %s", coll).isTrue();

        JsonNode row2 = arRow(partner);
        assertThat(row2.path("collected").asLong()).isEqualTo(50_000);
        assertThat(row2.path("balance").asLong()).isEqualTo(recGen - 50_000);
    }

    @Test
    @DisplayName("외상매출장 — 거래처 상세(매출/수금 명세 + 러닝밸런스)")
    void 외상매출장() {
        Long supplier = createId("/masters/clients", Map.of("code", "LG-SUP", "name", "입고공급사2", "type", "NORMAL"));
        Long wh = createId("/masters/warehouses", Map.of("code", "LG-WH", "name", "장부창고", "type", "MAIN"));
        Long p = createId("/masters/products",
                Map.of("code", "LG-BK", "name", "장부도서", "contentType", "SELF", "price", 10000));
        Long partner = createId("/masters/clients", Map.of("code", "LG-CUST", "name", "장부거래처", "type", "NORMAL"));
        inbound(wh, supplier, p);
        post("/sales/entries", Map.of(
                "salesDate", "2028-06-10", "partnerId", partner, "warehouseId", wh,
                "items", List.of(Map.of("productId", p, "shipmentType", "NORMAL_SHIP",
                        "qty", 5, "unitPrice", 10000, "supplyRate", 100))));
        post("/closing/collections", Map.of(
                "collDate", "2028-06-15", "partnerId", partner, "collType", "CASH", "collAmt", 20_000));

        JsonNode d = data(get("/closing/ar-ledger?partnerId=" + partner
                + "&fromDate=2028-01-01&toDate=2028-12-31"));
        assertThat(d.path("lines").size()).as("명세 라인(매출+수금): %s", d).isGreaterThanOrEqualTo(2);
        // 러닝밸런스 불변식: 기말잔액 = 이월 + Σ(라인 채권증감). 세액 포함여부 무관하게 성립해야 함.
        long sum = 0;
        for (JsonNode ln : d.path("lines")) {
            sum += ln.path("amount").asLong();
        }
        assertThat(d.path("closing").asLong()).isEqualTo(d.path("opening").asLong() + sum);
        // 마지막 라인 누계 == 기말잔액
        JsonNode last = d.path("lines").get(d.path("lines").size() - 1);
        assertThat(last.path("balance").asLong()).isEqualTo(d.path("closing").asLong());
    }

    @Test
    @DisplayName("매출 대시보드 — 목표대비 실적·달성률(상품별 격리)")
    void 대시보드_목표대비() {
        Long supplier = createId("/masters/clients", Map.of("code", "DB-SUP", "name", "입고공급사3", "type", "NORMAL"));
        Long wh = createId("/masters/warehouses", Map.of("code", "DB-WH", "name", "대시창고", "type", "MAIN"));
        Long mp = createId("/masters/products",
                Map.of("code", "DB-BK", "name", "대시도서", "contentType", "SELF", "price", 10000));
        Long partner = createId("/masters/clients", Map.of("code", "DB-CUST", "name", "대시거래처", "type", "NORMAL"));
        inbound(wh, supplier, mp);
        // 6월 순매출 100,000 (공급률100 × 10부)
        post("/sales/entries", Map.of(
                "salesDate", "2028-06-10", "partnerId", partner, "warehouseId", wh,
                "items", List.of(Map.of("productId", mp, "shipmentType", "NORMAL_SHIP",
                        "qty", 10, "unitPrice", 10000, "supplyRate", 100))));
        // 6월 목표 200,000 (상품별)
        JsonNode t = post("/dashboard/targets",
                Map.of("year", 2028, "month", 6, "productId", mp, "targetAmount", 200_000));
        assertThat(t.path("success").asBoolean()).as("목표설정: %s", t).isTrue();

        JsonNode d = data(get("/dashboard/sales?year=2028&productId=" + mp));
        JsonNode june = null;
        for (JsonNode m : d.path("months")) {
            if (m.path("month").asInt() == 6) {
                june = m;
            }
        }
        assertThat(june).as("6월 셀: %s", d).isNotNull();
        assertThat(june.path("target").asLong()).isEqualTo(200_000);
        assertThat(june.path("actual").asLong()).isEqualTo(100_000);        // 순매출
        assertThat(june.path("achievementPct").asDouble()).isEqualTo(50.0);  // 100k/200k
        assertThat(june.path("prevActual").asLong()).isEqualTo(0);           // 2027 없음
        // 연간 합계
        assertThat(d.path("summary").path("totalTarget").asLong()).isEqualTo(200_000);
        assertThat(d.path("summary").path("totalActual").asLong()).isEqualTo(100_000);
    }

    private JsonNode arRow(Long partnerId) {
        JsonNode rows = data(get("/closing/ar-status?fromDate=2028-01-01&toDate=2028-12-31&partnerId=" + partnerId))
                .path("rows");
        assertThat(rows).as("ar-status 행 존재").isNotEmpty();
        return rows.get(0);
    }

    private void inbound(Long whId, Long supplierId, Long productId) {
        JsonNode r = post("/stock/inbound", Map.of(
                "processedDate", "2028-06-01", "supplierClientId", supplierId, "destinationWarehouseId", whId,
                "items", List.of(Map.of("productId", productId, "unitCost", 3000, "qty", 100))));
        assertThat(r.path("success").asBoolean()).as("입고: %s", r).isTrue();
    }

    @Test
    @DisplayName("매출목표 — 연간 목표와 사업부문 축(발주처가 준 데이터 형태 그대로)")
    void 연간_사업부문_목표() {
        // 발주처 회신(자료요청서 1-6)은 월이 아니라 '연간', 대상은 상품이 아니라 '사업부문'으로 왔다.
        // 우리 양식이 월 단위 예시였던 탓에 구조가 월 필수였고, 그래서 한 줄도 못 넣던 것을 고쳤다.
        assertThat(post("/dashboard/targets", Map.of(
                "year", 2031, "scope", "DIVISION", "scopeKey", "더프리미엄",
                "targetAmount", 9_454_430_300L)).path("success").asBoolean()).isTrue();
        // 전년 실적: 레거시를 이관하지 않아 2030 매출이 DB에 없다 → 받은 실적값을 저장해 전년비를 살린다
        post("/dashboard/targets", Map.of(
                "year", 2030, "scope", "DIVISION", "scopeKey", "더프리미엄",
                "entryType", "ACTUAL", "targetAmount", 8_794_691_150L));

        JsonNode d = data(get("/dashboard/sales?year=2031&scope=DIVISION&scopeKey=더프리미엄"));
        assertThat(d.path("summary").path("totalTarget").asLong())
                .as("연간 목표가 연간 요약에 잡힌다").isEqualTo(9_454_430_300L);
        assertThat(d.path("summary").path("prevTotalActual").asLong())
                .as("전년 매출이 없으면 저장된 확정 실적으로 채운다").isEqualTo(8_794_691_150L);
        // ★연간 금액을 12로 나눠 월에 뿌리지 않는다 — 있지도 않은 월 목표를 만들어내면 달성률이 거짓이 된다
        for (JsonNode m : d.path("months")) {
            assertThat(m.path("target").asLong()).as("%s월 셀", m.path("month")).isZero();
        }
    }

    @Test
    @DisplayName("같은 연·대상·종류를 다시 등록하면 행이 늘지 않고 금액만 갱신된다")
    void 목표_중복등록() {
        post("/dashboard/targets", Map.of("year", 2032, "scope", "DIVISION",
                "scopeKey", "학원 컨텐츠", "targetAmount", 100));
        post("/dashboard/targets", Map.of("year", 2032, "scope", "DIVISION",
                "scopeKey", "학원 컨텐츠", "targetAmount", 200));

        JsonNode list = data(get("/dashboard/targets?year=2032"));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).path("amount").asLong()).isEqualTo(200);

        // 목표와 실적은 같은 연·대상이어도 별개 행이다(나란히 비교해야 하므로)
        post("/dashboard/targets", Map.of("year", 2032, "scope", "DIVISION",
                "scopeKey", "학원 컨텐츠", "entryType", "ACTUAL", "targetAmount", 300));
        assertThat(data(get("/dashboard/targets?year=2032"))).hasSize(2);
    }

    @Test
    @DisplayName("수금 기장일자 — 수금일자와 별개로 저장되고, 미입력이면 비어 있다")
    void 수금_기장일자() {
        // 레거시 AmtData.writeDate — 돈이 들어온 날과 장부에 기표한 날이 달라 재무팀이 둘을 나눠 본다.
        String sfx = "-WD" + (System.nanoTime() % 1_000_000L);
        Long p = createId("/masters/clients", Map.of("code", "WD" + sfx, "name", "수금처", "type", "NORMAL"));

        JsonNode a = data(post("/closing/collections", Map.of(
                "collDate", "2026-06-25", "writeDate", "2026-06-30",
                "partnerId", p, "collType", "CASH", "collAmt", 500_000)));
        assertThat(a.path("collDate").asText()).isEqualTo("2026-06-25");
        assertThat(a.path("writeDate").asText()).as("수금일자로 덮이지 않는다").isEqualTo("2026-06-30");

        // 미입력이면 수금일자로 자동으로 채우지 않는다 — 같다고 단정하면 따로 둘 이유가 없다.
        JsonNode b = data(post("/closing/collections", Map.of(
                "collDate", "2026-06-26", "partnerId", p, "collType", "CASH", "collAmt", 100_000)));
        assertThat(b.hasNonNull("writeDate")).isFalse();
    }

    @Test
    @DisplayName("담보만기 — 만료 건이 임박 건보다 앞에 모인다")
    void 담보만기_정렬() {
        // 발주처 확정(3-2 바): "만기 경과 건은 빨간색 표시 후 별도 목록으로 쏘팅".
        // 만기일 순으로만 두면 이미 지난 건과 아직 시간이 있는 건이 섞인다.
        String sfx = "-CE" + (System.nanoTime() % 1_000_000L);
        record Seed(String code, String expiry) { }
        for (Seed sd : List.of(new Seed("CE1", "2026-07-01"), new Seed("CE2", "2026-08-20"),
                new Seed("CE3", "2026-06-01"))) {
            Long id = createId("/masters/clients",
                    Map.of("code", sd.code() + sfx, "name", sd.code(), "type", "NORMAL"));
            put("/masters/clients/" + id, Map.of("name", sd.code(), "type", "NORMAL",
                    "assureAmount", 1_000_000, "assureExpiry", sd.expiry()));
        }

        List<String> statuses = new ArrayList<>();
        for (JsonNode r : data(get("/masters/clients/collateral-expiry?asOf=2026-08-12&withinDays=30"))
                .path("rows")) {
            if (r.path("code").asText().endsWith(sfx)) {
                statuses.add(r.path("status").asText());
            }
        }
        assertThat(statuses).containsExactly("EXPIRED", "EXPIRED", "IMMINENT");
    }

    @Test
    @DisplayName("대시보드 스냅샷 — 배치 전엔 실시간, 배치 후엔 스냅샷 기준시각이 붙는다")
    void 대시보드_스냅샷() {
        // 발주처 확정(3-2 아) "하루 1회 갱신". 다만 배치가 안 돈 날 화면이 비면 안 되므로
        // 스냅샷이 없으면 실시간으로 계산하고, computedAt으로 어느 쪽인지 드러낸다.
        String sfx = "-DS" + (System.nanoTime() % 1_000_000L);
        Long sup = createId("/masters/clients", Map.of("code", "DSS" + sfx, "name", "인쇄", "type", "NORMAL"));
        Long pt = createId("/masters/clients", Map.of("code", "DSP" + sfx, "name", "스냅처", "type", "NORMAL"));
        Long wh = createId("/masters/warehouses", Map.of("code", "DSW" + sfx, "name", "스냅창고", "type", "MAIN"));
        Long pr = createId("/masters/products", Map.of("code", "DSB" + sfx, "name", "스냅도서",
                "contentType", "SELF", "price", 10000, "supplyRate", 100));
        post("/stock/inbound", Map.of("processedDate", "2029-03-01", "supplierClientId", sup,
                "destinationWarehouseId", wh,
                "items", List.of(Map.of("productId", pr, "unitCost", 3000, "qty", 500))));
        post("/sales/entries", Map.of("salesDate", "2029-03-10", "partnerId", pt, "warehouseId", wh,
                "items", List.of(Map.of("productId", pr, "shipmentType", "NORMAL_SHIP", "qty", 100))));

        // 배치를 2029 기준으로 돌린다
        JsonNode run = data(post("/batch/jobs/dashboard-snapshot/run?baseDate=2029-12-31", null));
        assertThat(run.path("status").asText()).isEqualTo("SUCCESS");

        JsonNode d = data(get("/dashboard/sales?year=2029"));
        assertThat(d.hasNonNull("computedAt")).as("스냅샷을 썼으면 기준시각이 있다").isTrue();
        assertThat(d.path("summary").path("totalActual").asLong()).isEqualTo(1_000_000);

        // 같은 날 다시 돌려도 행이 늘지 않고 값만 갱신된다(지웠다 넣지 않는다).
        post("/sales/entries", Map.of("salesDate", "2029-03-11", "partnerId", pt, "warehouseId", wh,
                "items", List.of(Map.of("productId", pr, "shipmentType", "NORMAL_SHIP", "qty", 50))));
        post("/batch/jobs/dashboard-snapshot/run?baseDate=2029-12-31", null);
        assertThat(data(get("/dashboard/sales?year=2029")).path("summary").path("totalActual").asLong())
                .as("재실행하면 새 매출이 반영된다").isEqualTo(1_500_000);
    }
}
