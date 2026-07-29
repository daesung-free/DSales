package com.daesung.sales.master;

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
 * 기초마스터 확장 필드(32p) 통합테스트.
 * 도서(매출구분·수불부노출·Web게시) / 창고(실물재고여부·소속거래처) + 수불부노출 연동.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("기초마스터 확장 통합테스트")
class MasterFieldIntegrationTest extends IntegrationTestSupport {

    @BeforeAll
    void auth() {
        token();
    }

    @Test
    @DisplayName("도서 신규필드 — 매출구분·수불부노출·Web게시 저장/응답")
    void 도서_신규필드() {
        JsonNode d = data(post("/masters/products", Map.of(
                "code", "MF-BK1", "name", "확장필드 도서", "contentType", "SELF",
                "salesDivision", "정상", "ledgerVisible", false, "webVisible", true)));
        assertThat(d.path("salesDivision").asText()).isEqualTo("정상");
        assertThat(d.path("ledgerVisible").asBoolean()).isFalse();
        assertThat(d.path("webVisible").asBoolean()).isTrue();

        // 미지정 시 기본값(수불부노출 true, Web게시 false)
        JsonNode def = data(post("/masters/products", Map.of(
                "code", "MF-BK2", "name", "기본값 도서", "contentType", "SELF")));
        assertThat(def.path("ledgerVisible").asBoolean()).isTrue();
        assertThat(def.path("webVisible").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("창고 신규필드 — 실물재고여부·소속거래처 저장/응답")
    void 창고_신규필드() {
        Long owner = createId("/masters/clients",
                Map.of("code", "MF-OWN", "name", "소속거래처A", "type", "NORMAL"));
        JsonNode d = data(post("/masters/warehouses", Map.of(
                "code", "MF-WH-C", "name", "위탁창고", "type", "CONSIGN",
                "physicalStock", false, "ownerClientId", owner)));
        assertThat(d.path("physicalStock").asBoolean()).isFalse();
        assertThat(d.path("ownerClientId").asLong()).isEqualTo(owner);
        assertThat(d.path("ownerClientName").asText()).isEqualTo("소속거래처A");

        // 미지정 시 유형 기본값: CONSIGN→false
        JsonNode consign = data(post("/masters/warehouses",
                Map.of("code", "MF-WH-C2", "name", "위탁창고2", "type", "CONSIGN")));
        assertThat(consign.path("physicalStock").asBoolean()).isFalse();
        // MAIN→true
        JsonNode main = data(post("/masters/warehouses",
                Map.of("code", "MF-WH-M", "name", "물류창고", "type", "MAIN")));
        assertThat(main.path("physicalStock").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("수불부노출=false 도서는 제품수불부에서 제외")
    void 수불부노출_제외() {
        Long wh = createId("/masters/warehouses",
                Map.of("code", "MF-LWH", "name", "수불부창고", "type", "MAIN"));
        Long visible = createId("/masters/products", Map.of(
                "code", "MF-VIS", "name", "노출도서", "contentType", "SELF", "ledgerVisible", true));
        Long hidden = createId("/masters/products", Map.of(
                "code", "MF-HID", "name", "비노출도서", "contentType", "SELF", "ledgerVisible", false));
        inbound(wh, visible);
        inbound(wh, hidden);

        JsonNode rows = data(get("/stock/ledger?warehouseId=" + wh));
        assertThat(codes(rows)).contains("MF-VIS").doesNotContain("MF-HID");
    }

    @Test
    @DisplayName("재고관리 여부 — 모의고사(false)는 입고 없이 매출 성공·재고이벤트 없음")
    void 재고미관리_매출() {
        Long wh = createId("/masters/warehouses",
                Map.of("code", "MF-MSWH", "name", "모의고사창고", "type", "MAIN"));
        // 재고관리 안 함(모의고사) — 입고 전혀 없음
        Long exam = createId("/masters/products", Map.of(
                "code", "MF-EXAM", "name", "더프모의고사", "contentType", "SELF", "stockManaged", false));

        // 입고 0인데도 정상출고 매출 성공(NEGATIVE_STOCK 안 남)
        JsonNode r = post("/sales/entries", Map.of(
                "salesDate", "2026-04-15", "partnerId", ownerFallback(), "warehouseId", wh,
                "items", List.of(Map.of("productId", exam, "shipmentType", "NORMAL_SHIP",
                        "unitPrice", 10000, "supplyRate", 100, "qty", 30))));
        assertThat(r.path("success").asBoolean()).as("재고 미관리 상품 매출 성공: %s", r).isTrue();

        // 재고이벤트 없음 → 제품수불부에 해당 상품 행 없음
        JsonNode rows = data(get("/stock/ledger?warehouseId=" + wh));
        assertThat(codes(rows)).doesNotContain("MF-EXAM");

        // 대조: 재고관리 상품(기본 true)은 입고 없이 팔면 NEGATIVE_STOCK
        Long book = createId("/masters/products",
                Map.of("code", "MF-BOOK", "name", "일반교재", "contentType", "SELF"));
        JsonNode fail = post("/sales/entries", Map.of(
                "salesDate", "2026-04-15", "partnerId", ownerFallback(), "warehouseId", wh,
                "items", List.of(Map.of("productId", book, "shipmentType", "NORMAL_SHIP",
                        "unitPrice", 10000, "supplyRate", 100, "qty", 30))));
        assertThat(fail.path("success").asBoolean()).isFalse();
        assertThat(fail.path("error").path("code").asText()).isEqualTo("NEGATIVE_STOCK");
    }

    @Test
    @DisplayName("거래처별 단가·노출 매핑 — upsert/자동조회/삭제 + 단가 파생")
    void 거래처별단가매핑() {
        Long book = createId("/masters/products",
                Map.of("code", "PP-BK", "name", "단가매핑도서", "contentType", "SELF", "price", 10000));
        Long partner = createId("/masters/clients",
                Map.of("code", "PP-CUST", "name", "매핑거래처", "type", "NORMAL"));

        // 등록: 공급률 70 → 단가 = 10000×70/100 = 7000
        JsonNode d = data(put("/masters/products/" + book + "/partner-prices/" + partner,
                Map.of("supplyRate", 70, "visible", true)));
        assertThat(d.path("supplyRate").asInt()).isEqualTo(70);
        assertThat(d.path("unitPrice").asLong()).isEqualTo(7000);
        assertThat(d.path("partnerName").asText()).isEqualTo("매핑거래처");

        // 자동조회 단건
        JsonNode one = data(get("/masters/products/" + book + "/partner-prices/" + partner));
        assertThat(one.path("unitPrice").asLong()).isEqualTo(7000);

        // upsert(수정): 60 → 단가 6000, 중복 생성 아님(목록 1건)
        data(put("/masters/products/" + book + "/partner-prices/" + partner,
                Map.of("supplyRate", 60, "visible", false)));
        JsonNode list = data(get("/masters/products/" + book + "/partner-prices"));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).path("unitPrice").asLong()).isEqualTo(6000);
        assertThat(list.get(0).path("visible").asBoolean()).isFalse();

        // 삭제 후 자동조회 404
        del("/masters/products/" + book + "/partner-prices/" + partner);
        JsonNode gone = get("/masters/products/" + book + "/partner-prices/" + partner);
        assertThat(gone.path("success").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("마스터 엑셀 다운로드 — 거래처목록 xlsx(한글 헤더)")
    void 마스터엑셀() throws Exception {
        createId("/masters/clients", Map.of("code", "XL-CUST", "name", "엑셀거래처", "type", "NORMAL"));
        var resp = getBytes("/masters/clients/export");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        byte[] xlsx = resp.getBody();
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK");
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("거래처목록");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("거래처코드");
            assertThat(sheet.getLastRowNum()).isGreaterThan(0);
        }
    }

    @Test
    @DisplayName("담보 만기 알림 — 임박/만료 포함, 먼 만기는 제외")
    void 담보만기알림() {
        // 기준일 2026-06-01 고정. 임박(20일후)·만료(5일전)·먼미래(200일후)
        Long imminent = collateralPartner("COL-IMM", "임박거래처", "2026-06-21");
        Long expired = collateralPartner("COL-EXP", "만료거래처", "2026-05-27");
        collateralPartner("COL-FAR", "여유거래처", "2026-12-01");

        JsonNode rows = data(get("/masters/clients/collateral-expiry?asOf=2026-06-01&withinDays=30")).path("rows");
        JsonNode imm = rowByCode(rows, "COL-IMM");
        assertThat(imm.path("status").asText()).isEqualTo("IMMINENT");
        assertThat(imm.path("daysUntilExpiry").asLong()).isEqualTo(20);
        JsonNode exp = rowByCode(rows, "COL-EXP");
        assertThat(exp.path("status").asText()).isEqualTo("EXPIRED");
        assertThat(exp.path("daysUntilExpiry").asLong()).isEqualTo(-5);
        // 200일 후 만기는 30일 창에서 제외
        assertThat(codesOf(rows)).doesNotContain("COL-FAR");
    }

    private Long collateralPartner(String code, String name, String expiry) {
        Long id = createId("/masters/clients", Map.of("code", code, "name", name, "type", "NORMAL"));
        put("/masters/clients/" + id, Map.of(
                "name", name, "type", "NORMAL", "assureAmount", 50_000_000, "assureExpiry", expiry));
        return id;
    }

    private JsonNode rowByCode(JsonNode rows, String code) {
        for (JsonNode r : rows) {
            if (code.equals(r.path("code").asText())) {
                return r;
            }
        }
        throw new AssertionError("code=" + code + " 행 없음: " + rows);
    }

    private java.util.List<String> codesOf(JsonNode rows) {
        java.util.List<String> out = new java.util.ArrayList<>();
        rows.forEach(r -> out.add(r.path("code").asText()));
        return out;
    }

    private void inbound(Long whId, Long productId) {
        post("/stock/inbound", Map.of(
                "processedDate", "2026-06-01", "supplierClientId", ownerFallback(), "destinationWarehouseId", whId,
                "items", List.of(Map.of("productId", productId, "unitCost", 3000, "qty", 100))));
    }

    private Long ownerFallback = null;

    private Long ownerFallback() {
        if (ownerFallback == null) {
            ownerFallback = createId("/masters/clients",
                    Map.of("code", "MF-SUP", "name", "입고처", "type", "NORMAL"));
        }
        return ownerFallback;
    }

    private java.util.List<String> codes(JsonNode rows) {
        java.util.List<String> out = new java.util.ArrayList<>();
        rows.forEach(r -> out.add(r.path("productCode").asText()));
        return out;
    }
}
