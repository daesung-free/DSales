package com.daesung.sales.closing;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 계산서신고 홈택스 파일의 <b>품목 수량·단가</b>.
 *
 * <p>근거: 9/27 회의 항목 16 — "계산서신고 홈택스 양식이 <b>간소화된 것 같음</b>.
 * 기존 첨부 홈택스 양식은 더 상세함."
 *
 * <p>실제 양식(`세금계산신고_엑셀다운로드양식.xlsx`)과 대조해 보니 <b>컬럼 54개와 순서는 이미 일치</b>했고,
 * 비어 있던 것은 <b>값</b>이었다 — 규격·수량·단가 칸을 레거시가 통째로 비웠고 우리도 그대로 옮겼다.
 * 그중 수량·단가는 우리 매출 원장에 있는 값이라 채울 수 있다.
 *
 * <p>★<b>단가는 함부로 채우지 않는다.</b> 한 품목 칸은 그 거래처·도서의 <b>여러 매출 라인의 합</b>이라,
 * 라인마다 단가가 다르면 하나로 정할 수 없다. 그럴 때 임의값을 넣으면 단가×수량이 공급가액과
 * 어긋나 신고 파일이 틀린 것처럼 보인다 — 그래서 비운다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("홈택스 계산서 품목 수량·단가(항목 16)")
class TaxInvoiceItemDetailIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-TI" + (System.nanoTime() % 1_000_000L);
    private static final String FROM = "2095-08-01";
    private static final String TO = "2095-08-31";

    private Long partner;
    private Long warehouse;
    private Long bookSame;    // 같은 단가로만 팔린 도서
    private Long bookMixed;   // 단가가 섞인 도서

    @BeforeAll
    void seed() {
        token();
        Long supplier = createId("/masters/clients",
                Map.of("code", "TIS" + SFX, "name", "인쇄소", "type", "NORMAL"));
        partner = createId("/masters/clients", Map.of(
                "code", "TIP" + SFX, "name", "홈택스검증거래처", "type", "NORMAL",
                "bizNo", "220-81-62517", "bossName", "김대표"));
        warehouse = createId("/masters/warehouses",
                Map.of("code", "TIW" + SFX, "name", "홈택스검증창고", "type", "MAIN"));

        bookSame = book("TIA" + SFX, "단일단가 교재");
        bookMixed = book("TIB" + SFX, "혼합단가 교재");
        for (Long p : List.of(bookSame, bookMixed)) {
            post("/stock/inbound", Map.of("processedDate", FROM, "supplierClientId", supplier,
                    "destinationWarehouseId", warehouse,
                    "items", List.of(Map.of("productId", p, "unitCost", 1000, "qty", 1000))));
        }

        // 같은 단가 10,000 × 두 건 → 수량 30, 단가 10,000 으로 특정된다
        sale(bookSame, 10_000, 70, 10);
        sale(bookSame, 10_000, 70, 20);
        // 단가가 다른 두 건 → 단가를 하나로 정할 수 없다
        sale(bookMixed, 10_000, 70, 5);
        sale(bookMixed, 12_000, 70, 5);
    }

    private Long book(String code, String name) {
        Map<String, Object> b = new HashMap<>();
        b.put("code", code);
        b.put("name", name);
        b.put("contentType", "SELF");
        b.put("price", 10000);
        b.put("supplyRate", 70);
        b.put("catCode", "T2095A01");
        b.put("catName", "홈택스검증");
        b.put("taxFree", true);   // 면세 → '05' 시트
        return createId("/masters/products", b);
    }

    private void sale(Long productId, int unitPrice, int rate, int qty) {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", productId);
        item.put("shipmentType", "NORMAL_SHIP");
        item.put("unitPrice", unitPrice);
        item.put("supplyRate", rate);
        item.put("qty", qty);
        JsonNode r = post("/sales/entries", Map.of(
                "salesDate", "2095-08-10", "partnerId", partner, "warehouseId", warehouse,
                "items", List.of(item)));
        assertThat(r.path("success").asBoolean()).as("매출등록: %s", r).isTrue();
    }

    private JsonNode itemOf(String bookName) {
        JsonNode d = data(get("/closing/tax-invoices?fromDate=" + FROM + "&toDate=" + TO
                + "&partnerId=" + partner));
        for (JsonNode inv : d.path("invoices")) {
            for (JsonNode it : inv.path("items")) {
                if (it.path("name").asText().contains(bookName)) {
                    return it;
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("★수량은 응답에 실린다 — 엑셀 칸은 비우지만 데이터는 갖고 있다")
    void 수량이_실린다() {
        JsonNode it = itemOf("단일단가");
        assertThat(it).isNotNull();
        assertThat(it.path("qty").asLong()).as("10 + 20").isEqualTo(30);
        assertThat(it.path("supply").asLong()).as("7,000 × 30").isEqualTo(210_000);
    }

    @Test
    @DisplayName("★단가가 하나로 정해질 때만 싣는다 — 섞여 있으면 비운다")
    void 단가는_정해질때만() {
        assertThat(itemOf("단일단가").path("unitPrice").asLong())
                .as("두 건이 같은 단가라 특정된다").isEqualTo(10_000);

        JsonNode mixed = itemOf("혼합단가");
        assertThat(mixed).isNotNull();
        // ‼️Jackson 전역 NON_NULL 이라 null 이면 키 자체가 사라진다.
        assertThat(mixed.hasNonNull("unitPrice"))
                .as("단가가 10,000/12,000 로 갈려 하나로 못 정한다 — 임의값을 넣으면 "
                        + "단가×수량이 공급가액과 어긋난다").isFalse();
        assertThat(mixed.path("qty").asLong()).as("수량은 합산된다").isEqualTo(10);
    }

    @Test
    @DisplayName("★홈택스 파일의 수량·단가는 **비운다** — 기존 프로그램과 같게(2026-09-28 확정)")
    void 파일은_비운다() throws Exception {
        var res = getBytes("/closing/tax-invoices/export?fromDate=" + FROM + "&toDate=" + TO
                + "&partnerId=" + partner);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isNotNull();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(res.getBody()))) {
            Sheet free = wb.getSheet("면세(05)");
            assertThat(free).as("면세 시트").isNotNull();

            List<String> header = cells(free.getRow(0));
            // 실제 양식(세금계산신고_엑셀다운로드양식.xlsx)과 같은 54칸·같은 순서여야 한다.
            assertThat(header).hasSize(54);
            assertThat(header.get(21)).isEqualTo("일자1");
            assertThat(header.get(24)).isEqualTo("수량1");
            assertThat(header.get(25)).isEqualTo("단가1");

            List<String> row = cells(free.getRow(1));
            assertThat(row).as("계산서 한 장은 나와야 한다").isNotEmpty();

            // ★품목이 실린 슬롯의 규격·수량·단가는 모두 비어 있어야 한다.
            //   홈택스는 단가×수량이 공급가액과 맞아야 하는데, 한 품목 칸이 여러 매출의 합이라
            //   단가가 갈리는 건이 생긴다 — 수량만 있고 단가가 빈 줄은 반려 사유가 될 수 있다.
            boolean sawItem = false;
            for (int slot = 0; slot < 4; slot++) {
                int base = 21 + slot * 7;      // 일자·품목·규격·수량·단가·공급가액·비고
                if (row.get(base + 1).isBlank()) {
                    continue;                  // 빈 슬롯
                }
                sawItem = true;
                assertThat(row.get(base + 2)).as("규격").isEmpty();
                assertThat(row.get(base + 3)).as("수량").isEmpty();
                assertThat(row.get(base + 4)).as("단가").isEmpty();
                assertThat(row.get(base + 5)).as("공급가액은 채운다").isNotEmpty();
            }
            assertThat(sawItem).as("품목이 한 줄은 실려야 한다: %s", row).isTrue();
        }
    }

    /** 셀 값을 문자열로. 숫자는 소수점 없이(엑셀은 30을 30.0으로 들고 있다). */
    private static List<String> cells(Row row) {
        List<String> out = new ArrayList<>();
        if (row == null) {
            return out;
        }
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell c = row.getCell(i);
            if (c == null) {
                out.add("");
            } else if (c.getCellType() == CellType.NUMERIC) {
                out.add(String.valueOf((long) c.getNumericCellValue()));
            } else {
                out.add(c.getStringCellValue());
            }
        }
        return out;
    }
}
