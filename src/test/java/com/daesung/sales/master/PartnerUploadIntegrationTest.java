package com.daesung.sales.master;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.util.List;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/**
 * 거래처 <b>엑셀 업로드</b>(30p) 회귀 고정.
 *
 * <p>★고정하려는 것은 넷이다.
 * <ol>
 *   <li>내려받은 <b>헤더 그대로</b> 올리면 들어간다 — 담보·주민번호까지.</li>
 *   <li><b>빈 칸은 그대로</b>다. 거래처 수정 API가 전체 교체라 실제로 값을 날린 적이 있다
 *       — 업로드에서 같은 사고가 나면 수백 건이 한 번에 지워진다.</li>
 *   <li>등록 화면과 달리 <b>사업자번호 형식을 검증하지 않는다</b>(의도).
 *       초기 적재 원본에 섞인 마스킹·옛 표기 행을 거부하면 한 건도 못 올린다.</li>
 *   <li><b>거래처코드 열이 없는 파일</b>은 행 단위가 아니라 파일 전체를 거부한다.</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("거래처 엑셀 업로드(30p)")
class PartnerUploadIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-PU" + (System.nanoTime() % 1_000_000L);

    private String codeA;
    private String codeB;

    @BeforeAll
    void seed() {
        token();
        codeA = "PUA" + SFX;
        codeB = "PUB" + SFX;
    }

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
                        continue;                       // 빈 칸 = 그대로
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

    private JsonNode partner(String code) {
        for (JsonNode n : data(get("/masters/clients?keyword=" + code + "&size=50&includeExpired=true"))
                .path("content")) {
            if (code.equals(n.path("code").asText())) {
                return n;
            }
        }
        return null;
    }

    @Test
    @Order(1)
    @DisplayName("★목록 다운로드 헤더 그대로 올리면 등록된다 — 담보·주민번호 포함")
    void 신규등록() {
        byte[] f = xlsx(
                List.of("거래처코드", "거래처명", "신고명", "사업자번호", "업종", "업태", "사업자명",
                        "사업자주민번호", "연락처1", "기본주소", "지역", "관할지역",
                        "담보금액", "담보만기", "담보내용", "등록일"),
                List.of(
                        List.of(codeA, "업로드상사", "업로드", "118-90-16181", "도매", "서적", "홍길동",
                                "8001011234567", "02-1234-5678", "서울시 구로구", "서울", "구로구",
                                50000000L, "2027-11-18", "한화생명 저축보험", "2016-01-05"),
                        List.of(codeB, "업로드문고", "업로드2", "113-90-57983", "도소매", "서적", "김철수",
                                "", "02-2222-3333", "부산시 해운대구", "부산", "해운대구",
                                "", "", "", "2018-03-02")));

        JsonNode d = data(multipart("/masters/clients/upload", f, "clients.xlsx"));
        assertThat(d.path("created").asInt()).as("결과: %s", d).isEqualTo(2);
        assertThat(d.path("failed").asInt()).isZero();

        JsonNode a = partner(codeA);
        assertThat(a).isNotNull();
        assertThat(a.path("bizNo").asText()).isEqualTo("118-90-16181");
        assertThat(a.path("assureAmount").asLong()).isEqualTo(50_000_000L);
        assertThat(a.path("assureExpiry").asText()).isEqualTo("2027-11-18");
        assertThat(a.path("region").asText()).isEqualTo("서울");
        assertThat(a.path("zone2").asText()).isEqualTo("구로구");
        assertThat(a.path("bossId").asText())
                .as("주민번호는 저장되되 응답은 마스킹돼 나간다").contains("*");
    }

    @Test
    @Order(2)
    @DisplayName("★빈 칸은 그대로 — 연락처만 고쳐도 담보가 날아가면 안 된다")
    void 빈칸은_유지() {
        byte[] f = xlsx(List.of("연락처1", "거래처코드"),
                List.of(List.of("02-9999-0000", codeA)));

        JsonNode d = data(multipart("/masters/clients/upload", f, "clients.xlsx"));
        assertThat(d.path("updated").asInt()).as("신규가 아니라 수정: %s", d).isEqualTo(1);
        assertThat(d.path("created").asInt()).isZero();

        JsonNode a = partner(codeA);
        assertThat(a.path("tel1").asText()).isEqualTo("02-9999-0000");
        assertThat(a.path("assureAmount").asLong()).as("담보금액이 유지돼야 한다").isEqualTo(50_000_000L);
        assertThat(a.path("name").asText()).isEqualTo("업로드상사");
        assertThat(a.path("bizNo").asText()).isEqualTo("118-90-16181");
        assertThat(a.path("addr1").asText()).isEqualTo("서울시 구로구");
    }

    @Test
    @Order(3)
    @DisplayName("★사업자번호 형식은 검증하지 않는다 — 등록 화면은 400이지만 업로드는 통과(의도)")
    void 형식검증_없음() {
        String code = "PUM" + SFX;

        // 등록 API는 같은 값을 거부한다.
        var rejected = exchangeRaw(HttpMethod.POST, "/masters/clients",
                java.util.Map.of("code", code, "name", "마스킹상사", "bizNo", "108-**-***57"),
                token(), null);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 업로드는 원본 그대로 싣는다.
        byte[] f = xlsx(List.of("거래처코드", "거래처명", "사업자번호"),
                List.of(List.of(code, "마스킹상사", "108-**-***57")));
        JsonNode d = data(multipart("/masters/clients/upload", f, "clients.xlsx"));
        assertThat(d.path("created").asInt()).as("결과: %s", d).isEqualTo(1);
        assertThat(partner(code).path("bizNo").asText()).isEqualTo("108-**-***57");
    }

    @Test
    @Order(4)
    @DisplayName("★거래처코드 열이 없으면 파일 전체를 거부한다")
    void 키열_없는_파일() {
        byte[] f = xlsx(List.of("거래처명", "사업자번호"), List.of(List.of("이름만있음", "111-11-11111")));
        JsonNode res = multipart("/masters/clients/upload", f, "clients.xlsx");
        assertThat(res.path("success").asBoolean()).as("파일 전체 거부: %s", res).isFalse();
        assertThat(res.path("error").toString()).contains("거래처코드");
    }
}
