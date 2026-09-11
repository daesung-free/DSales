package com.daesung.sales.sale;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 개발팀 점검(2026-09-11) 서버 결함 회귀 고정.
 *
 * <ol>
 *   <li><b>매출 엑셀 업로드가 구조적으로 100% 실패</b> — 분류코드와 도서코드를 문자열로
 *       이어붙여({@code catCode + code}) 전역 도서코드와 대조했다. {@code K202601BK-K2026-1}
 *       같은 값을 찾게 되어 <b>어떤 마스터로도 맞지 않는다.</b></li>
 *   <li>{@code GET /sales} 에 키워드·창고 필터가 없어 두 화면이 못 씀</li>
 *   <li>{@code GET /stock/ledger} 에 키워드·페이징이 없어 전량 반환</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("업로드 상품조회 · 조회 필터(2026-09-11 점검)")
class UploadAndFilterIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-UF" + (System.nanoTime() % 1_000_000L);
    private static final int YEAR = 2072;
    private static final String CAT = "U" + YEAR + "A";

    private Long partner;
    private Long wh;
    private String bookCode;

    @BeforeAll
    void seed() {
        token();
        partner = createId("/masters/clients", Map.of("code", "UFC" + SFX, "name", "업로드거래처", "type", "NORMAL"));
        Long sup = createId("/masters/clients", Map.of("code", "UFS" + SFX, "name", "인쇄", "type", "NORMAL"));
        wh = createId("/masters/warehouses", Map.of("code", "UFW" + SFX, "name", "업로드창고", "type", "MAIN"));

        bookCode = "UFB" + SFX;
        Map<String, Object> b = new HashMap<>();
        b.put("code", bookCode);
        b.put("name", "업로드도서");
        b.put("contentType", "SELF");
        b.put("price", 10000);
        b.put("supplyRate", 70);
        b.put("catCode", CAT);
        b.put("catName", "업로드분류");
        createId("/masters/products", b);

        post("/stock/inbound", Map.of("processedDate", YEAR + "-01-05", "supplierClientId", sup,
                "destinationWarehouseId", wh,
                "items", List.of(Map.of("productId", productId(), "unitCost", 3000, "qty", 500))));

        // ★키워드·창고 필터 검증용 매출은 여기서 직접 만든다.
        //   업로드 테스트가 만든 것에 기대면 실행 순서에 따라 있기도 없기도 하다.
        post("/sales/entries", Map.of("salesDate", YEAR + "-02-10", "partnerId", partner,
                "warehouseId", wh,
                "items", List.of(Map.of("productId", productId(), "shipmentType", "NORMAL_SHIP",
                        "unitPrice", 10000, "supplyRate", 70, "qty", 5))));
    }

    private long productId() {
        for (JsonNode n : data(get("/masters/products?keyword=" + bookCode + "&size=10")).path("content")) {
            if (bookCode.equals(n.path("code").asText())) {
                return n.path("id").asLong();
            }
        }
        throw new IllegalStateException("도서 없음");
    }

    /** 발주처 표준양식 12컬럼. */
    private byte[] sheet(String catCode, String code) {
        String[] head = {"거래일자", "거래처코드", "학교코드", "분류코드", "도서코드", "회차",
                "정가", "공급률(%)", "수량", "금액", "구분(출고유형)", "메모"};
        Object[] row = {YEAR + "-03-05", "UFC" + SFX, "", catCode, code, "",
                10000, 0.7, 10, 70000, "정상출고", "테스트"};
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet s = wb.createSheet("s");
            Row h = s.createRow(0);
            for (int i = 0; i < head.length; i++) {
                h.createCell(i).setCellValue(head[i]);
            }
            Row r = s.createRow(1);
            for (int i = 0; i < row.length; i++) {
                if (row[i] instanceof Number n) {
                    r.createCell(i).setCellValue(n.doubleValue());
                } else {
                    r.createCell(i).setCellValue(String.valueOf(row[i]));
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("★분류코드 + 도서코드로 상품을 찾는다 — 이어붙이면 어떤 마스터로도 안 맞는다")
    void 업로드_상품조회() {
        JsonNode d = data(multipart("/sales/upload", sheet(CAT, bookCode), "up.xlsx"));

        assertThat(d.path("imported").asInt()).as("%s", d).isEqualTo(1);
        assertThat(d.path("failed").asInt()).isZero();
    }

    @Test
    @DisplayName("분류코드가 비어도 도서코드 단독으로 찾는다 — 전역 고유코드 체계도 받는다")
    void 도서코드_단독() {
        JsonNode d = data(multipart("/sales/upload", sheet("", bookCode), "up.xlsx"));

        assertThat(d.path("imported").asInt()).as("%s", d).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 도서는 행 단위 오류 — 어느 줄인지 분류코드와 함께 알려준다")
    void 없는_도서() {
        JsonNode d = data(multipart("/sales/upload", sheet(CAT, "없는코드"), "up.xlsx"));

        assertThat(d.path("failed").asInt()).isEqualTo(1);
        assertThat(d.path("lines").get(0).path("message").asText()).contains("도서 없음");
    }

    @Test
    @DisplayName("★매출 조회에 키워드가 먹는다 — 거래처명·도서명·도서코드를 함께 훑는다")
    void 매출_키워드() {
        String range = "?startDate=" + YEAR + "-01-01&endDate=" + YEAR + "-12-31&size=100";

        assertThat(data(get("/sales" + range + "&keyword=업로드도서")).path("content"))
                .as("도서명으로").isNotEmpty();
        assertThat(data(get("/sales" + range + "&keyword=" + bookCode)).path("content"))
                .as("도서코드로").isNotEmpty();
        assertThat(data(get("/sales" + range + "&keyword=없는검색어xyz")).path("content"))
                .as("안 맞으면 빈 결과").isEmpty();
    }

    @Test
    @DisplayName("매출 조회에 창고 필터가 먹는다")
    void 매출_창고필터() {
        String range = "?startDate=" + YEAR + "-01-01&endDate=" + YEAR + "-12-31&size=100";

        assertThat(data(get("/sales" + range + "&warehouseIds=" + wh)).path("content")).isNotEmpty();
        assertThat(data(get("/sales" + range + "&warehouseIds=999999")).path("content")).isEmpty();
    }

    @Test
    @DisplayName("★수불부가 페이지 형태로 오고 키워드가 먹는다")
    void 수불부_페이징과_키워드() {
        JsonNode d = data(get("/stock/ledger?fromDate=" + YEAR + "-01-01&toDate=" + YEAR + "-12-31"
                + "&keyword=" + bookCode + "&page=0&size=10"));

        assertThat(d.has("content")).as("배열이 아니라 페이지 형태여야 한다").isTrue();
        assertThat(d.path("content")).isNotEmpty();
        assertThat(d.path("totalElements").asLong()).isPositive();

        assertThat(data(get("/stock/ledger?fromDate=" + YEAR + "-01-01&toDate=" + YEAR + "-12-31"
                + "&keyword=없는도서xyz")).path("content")).isEmpty();
    }
}
