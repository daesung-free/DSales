package com.daesung.sales.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * 도서관리 3탭 <b>엑셀 업로드</b>(33p) 회귀 고정.
 * 근거: 발주처 화면검토(2026-08-31) 화면33 — "도서관리 등록 탭(기본정보/세트구성/거래처별단가)
 * 전체에 엑셀 업로드 기능 신설".
 *
 * <p>★고정하려는 것은 다섯이다.
 * <ol>
 *   <li>내려받은 <b>헤더 그대로</b> 올리면 들어간다(왕복).</li>
 *   <li><b>열 순서를 바꿔도</b> 된다 — 이름으로 읽기 때문. 위치로 읽으면 값이 조용히 뒤바뀐다.</li>
 *   <li><b>빈 칸은 그대로</b>다 — 정가만 고치려다 이름·분류를 날리면 안 된다.</li>
 *   <li>한 행이 틀려도 <b>나머지는 들어간다</b>. 단 <b>필수 열이 없는 파일</b>은 통째로 거부.</li>
 *   <li>BOM은 <b>세트 단위</b>로 실패한다 — 반쪽 BOM은 조립할 때 자재가 안 빠진다.</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// ★순서가 있다 — 앞 테스트가 만든 도서를 뒤 테스트가 수정한다.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("도서관리 3탭 엑셀 업로드(33p)")
class MasterUploadIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-UP" + (System.nanoTime() % 1_000_000L);
    private static final String CAT = "U2065" + (System.nanoTime() % 100);

    private String bookA;
    private String bookB;
    private String setCode;
    private String partnerCode;

    @BeforeAll
    void seed() {
        token();
        bookA = "UPA" + SFX;
        bookB = "UPB" + SFX;
        setCode = "UPSET" + SFX;
        partnerCode = "UPP" + SFX;
        createId("/masters/clients", Map.of("code", partnerCode, "name", "업로드거래처", "type", "NORMAL"));
    }

    /** 헤더 이름과 행 값으로 xlsx 바이트를 만든다. */
    private static byte[] xlsx(List<String> headers, List<List<Object>> rows) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("s");
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                h.createCell(i).setCellValue(headers.get(i));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> vals = rows.get(r);
                for (int c = 0; c < vals.size(); c++) {
                    Object v = vals.get(c);
                    if (v == null) {
                        continue;                      // 빈 칸 = 그대로
                    }
                    if (v instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    } else {
                        row.createCell(c).setCellValue(String.valueOf(v));
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode book(String code) {
        for (JsonNode n : data(get("/masters/products?keyword=" + code + "&size=50")).path("content")) {
            if (code.equals(n.path("code").asText())) {
                return n;
            }
        }
        return null;
    }

    @Test
    @Order(1)
    @DisplayName("★기본정보 — 내려받은 헤더 그대로 올리면 등록된다")
    void 기본정보_신규등록() {
        byte[] f = xlsx(
                List.of("도서코드", "도서명", "콘텐츠구분", "분류코드", "분류명", "정가", "공급률", "수불부노출"),
                List.of(List.of(bookA, "업로드도서A", "자체교재", CAT, "업로드분류", 12000, 70, "Y"),
                        List.of(bookB, "업로드도서B", "매입 교재", CAT, "업로드분류", 8000, 60, "N"),
                        List.of(setCode, "업로드세트", "자체교재", CAT, "업로드분류", 20000, 75, "Y")));

        JsonNode d = data(upload("/masters/products/upload", f, "books.xlsx"));
        assertThat(d.path("created").asInt()).isEqualTo(3);
        assertThat(d.path("failed").asInt()).isZero();

        assertThat(book(bookA).path("price").asInt()).isEqualTo(12000);
        assertThat(book(bookB).path("contentType").asText())
                .as("한글 라벨('매입 교재')로도 읽혀야 한다").isEqualTo("EXTERNAL");
        assertThat(book(bookB).path("ledgerVisible").asBoolean()).isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("★열 순서를 바꿔도 된다 · 빈 칸은 그대로 — 위치로 읽으면 값이 조용히 뒤바뀐다")
    void 열순서와_빈칸() {
        // 정가만 고친다. 열 순서도 뒤섞고 도서명·분류는 아예 안 넣는다.
        byte[] f = xlsx(List.of("정가", "도서코드"),
                List.of(List.of(15000, bookA)));

        JsonNode d = data(upload("/masters/products/upload", f, "books.xlsx"));
        assertThat(d.path("updated").asInt()).as("신규가 아니라 수정: %s", d).isEqualTo(1);
        assertThat(d.path("created").asInt()).isZero();

        JsonNode a = book(bookA);
        assertThat(a.path("price").asInt()).as("고친 값").isEqualTo(15000);
        assertThat(a.path("name").asText()).as("‼️안 적은 칸은 그대로여야 한다").isEqualTo("업로드도서A");
        assertThat(a.path("catCode").asText()).isEqualTo(CAT);
        assertThat(a.path("supplyRate").asInt()).isEqualTo(70);
    }

    @Test
    @Order(3)
    @DisplayName("한 행이 틀려도 나머지는 들어간다 — 수백 줄에서 오타 하나로 전부 되돌리면 못 쓴다")
    void 부분_실패() {
        byte[] f = xlsx(List.of("도서코드", "도서명", "정가", "수불부노출"),
                List.of(List.of(bookA, "업로드도서A", 16000, "Y"),
                        List.of(bookB, "업로드도서B", 9000, "Yes")));   // Y/N이 아니다

        JsonNode d = data(upload("/masters/products/upload", f, "books.xlsx"));
        assertThat(d.path("updated").asInt()).isEqualTo(1);
        assertThat(d.path("failed").asInt()).isEqualTo(1);

        JsonNode err = d.path("lines").get(1);
        assertThat(err.path("result").asText()).isEqualTo("ERROR");
        assertThat(err.path("row").asInt()).as("엑셀 행번호를 그대로 줘야 그 줄을 찾는다").isEqualTo(3);
        assertThat(err.path("message").asText()).contains("Y/N");

        assertThat(book(bookA).path("price").asInt()).as("성공한 행은 남는다").isEqualTo(16000);
    }

    @Test
    @Order(4)
    @DisplayName("★필수 열이 없는 파일은 통째로 거부 — 행 단위 오류로 흘리면 수백 줄 보고서야 안다")
    void 필수열_누락() {
        byte[] f = xlsx(List.of("정가", "분류명"), List.of(List.of(1000, "아무거나")));

        JsonNode r = upload("/masters/products/upload", f, "books.xlsx");
        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").path("message").asText()).contains("필수 컬럼이 없습니다", "도서코드");
    }

    @Test
    @Order(5)
    @DisplayName("★세트구성 — 여러 행이 한 세트로 묶인다")
    void 세트구성_업로드() {
        byte[] f = xlsx(List.of("세트도서코드", "구성도서코드", "소요수량", "자재구분"),
                List.of(List.of(setCode, bookA, 2, "시험지"),
                        List.of(setCode, bookB, 3, "해설지")));

        JsonNode d = data(upload("/masters/products/bom/upload", f, "bom.xlsx"));
        assertThat(d.path("created").asInt()).as("2행: %s", d).isEqualTo(2);
        assertThat(d.path("failed").asInt()).isZero();

        Long setId = book(setCode).path("id").asLong();
        JsonNode bom = data(get("/masters/products/" + setId + "/bom")).path("components");
        assertThat(bom).hasSize(2);
    }

    @Test
    @Order(6)
    @DisplayName("★BOM은 세트 단위로 실패한다 — 반쪽 BOM은 조립할 때 자재가 안 빠진다")
    void 세트단위_실패() {
        byte[] f = xlsx(List.of("세트도서코드", "구성도서코드", "소요수량"),
                List.of(List.of(setCode, bookA, 5),
                        List.of(setCode, "없는도서코드", 1)));   // 이 한 줄 때문에 세트 전체가 무효

        JsonNode d = data(upload("/masters/products/bom/upload", f, "bom.xlsx"));
        assertThat(d.path("failed").asInt()).as("두 행 모두 실패: %s", d).isEqualTo(2);

        // ★기존 BOM(2건)이 그대로 살아 있어야 한다 — 반쪽으로 덮어쓰지 않았다
        Long setId = book(setCode).path("id").asLong();
        assertThat(data(get("/masters/products/" + setId + "/bom")).path("components"))
                .as("‼️1건짜리 반쪽 BOM이 됐으면 조립 시 자재가 안 빠진다").hasSize(2);
    }

    @Test
    @Order(7)
    @DisplayName("★거래처별 단가 — 키는 거래처 × 대분류(도서 단위가 아니다)")
    void 거래처별단가_업로드() {
        byte[] f = xlsx(List.of("거래처코드", "대분류", "공급률", "사용여부"),
                List.of(List.of(partnerCode, "교재", 60, "Y"),
                        List.of(partnerCode, "모의고사", 75, "Y")));

        JsonNode d = data(upload("/masters/partner-supply-rates/upload", f, "rates.xlsx"));
        assertThat(d.path("created").asInt()).as("%s", d).isEqualTo(2);

        JsonNode got = data(get("/masters/partner-supply-rates/"
                + partnerId() + "/TEXTBOOK"));
        assertThat(got.path("supplyRate").asInt()).isEqualTo(60);

        // 같은 파일을 다시 올리면 수정으로 잡힌다 — 숫자만 봐도 키가 맞았는지 안다
        JsonNode again = data(upload("/masters/partner-supply-rates/upload", f, "rates.xlsx"));
        assertThat(again.path("updated").asInt()).isEqualTo(2);
        assertThat(again.path("created").asInt()).isZero();
    }

    private long partnerId() {
        for (JsonNode n : data(get("/masters/clients?keyword=" + partnerCode + "&size=50")).path("content")) {
            if (partnerCode.equals(n.path("code").asText())) {
                return n.path("id").asLong();
            }
        }
        throw new IllegalStateException("거래처 없음");
    }

    private JsonNode upload(String path, byte[] bytes, String filename) {
        return multipart(path, bytes, filename);
    }
}
