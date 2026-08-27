package com.daesung.sales.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.excel.ExcelExportUtil.Heading;
import com.daesung.sales.common.excel.ExcelExportUtil.Subtotal;
import com.daesung.sales.support.IntegrationTestSupport;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
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
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 엑셀 <b>제목·조회기준 2행</b>과 <b>분류 소계</b> 회귀 고정.
 * 근거: 프론트 「백엔드 전달 2026-08-20」 §A-2 —
 * "{@code ExcelExportUtil}은 0행부터 헤더를 쓰고 소계 개념이 없다. 지금 상태로 서버 export에
 * 일원화하면 재무팀 실파일 대조로 반영한 제목·조회기준 2행과 매출액정리 분류별 소계가 사라진다".
 *
 * <p>★재무팀은 <b>기존 파일과 눈으로 대조</b>하는 환경이라 형식 차이가 곧 클레임이다.
 * 그래서 "값이 맞다"가 아니라 <b>몇 번째 줄에 무엇이 있는지</b>를 본다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("엑셀 제목 2행·분류 소계")
class ExcelHeadingIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ExcelExportUtil excel;

    private static final List<Col> COLS = List.of(
            new Col("분류코드", "catCode"), new Col("분류명", "catName"),
            new Col("도서명", "bookName"), new Col("수량", "qty"), new Col("금액", "amount"));

    @BeforeAll
    void auth() {
        token();
    }

    private static Sheet sheetOf(byte[] xlsx) throws Exception {
        Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx));
        return wb.getSheetAt(0);
    }

    private static String str(Sheet s, int r, int c) {
        Row row = s.getRow(r);
        return (row == null || row.getCell(c) == null) ? null : row.getCell(c).getStringCellValue();
    }

    private static double num(Sheet s, int r, int c) {
        return s.getRow(r).getCell(c).getNumericCellValue();
    }

    @Test
    @DisplayName("★제목·조회기준이 헤더 위 2행에 온다 — 데이터는 4행부터")
    void 제목_2행() throws Exception {
        Sheet s = sheetOf(excel.toXlsx("시트", COLS,
                List.of(Map.of("catCode", "A2026A", "catName", "국어", "bookName", "책", "qty", 1, "amount", 100)),
                Heading.period("외상매출현황조회", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30))));

        assertThat(str(s, 0, 0)).as("1행=제목").isEqualTo("외상매출현황조회");
        assertThat(str(s, 1, 0)).as("2행=조회기준(재무팀 파일과 같은 점 구분)")
                .isEqualTo("조회기준 : 2026.01.01 ~ 2026.06.30");
        assertThat(str(s, 2, 0)).as("3행=헤더").isEqualTo("분류코드");
        assertThat(str(s, 3, 2)).as("4행부터 데이터").isEqualTo("책");
    }

    @Test
    @DisplayName("머리글을 안 주면 예전처럼 0행이 헤더다 — 기존 export가 밀리지 않는다")
    void 머리글_없으면_그대로() throws Exception {
        Sheet s = sheetOf(excel.toXlsx("시트", COLS,
                List.of(Map.of("catCode", "A2026A", "catName", "국어", "bookName", "책", "qty", 1, "amount", 100))));

        assertThat(str(s, 0, 0)).isEqualTo("분류코드");
        assertThat(str(s, 1, 2)).isEqualTo("책");
    }

    @Test
    @DisplayName("★분류가 바뀌는 자리에 '소 계', 맨 끝에 '총 계'가 들어간다")
    void 분류_소계와_총계() throws Exception {
        Sheet s = sheetOf(excel.toXlsx("시트", COLS, List.of(
                Map.of("catCode", "A2026A", "catName", "국어", "bookName", "국어1", "qty", 10, "amount", 1000),
                Map.of("catCode", "A2026A", "catName", "국어", "bookName", "국어2", "qty", 20, "amount", 2000),
                Map.of("catCode", "B2026A", "catName", "수학", "bookName", "수학1", "qty", 5, "amount", 500)),
                null, new Subtotal("catCode", "catName", List.of("qty", "amount"))));

        // 0=헤더 / 1,2=국어 2행 / 3=국어 소계 / 4=수학 1행 / 5=수학 소계 / 6=총계
        assertThat(str(s, 3, 0)).isEqualTo("국어 소 계");
        assertThat(num(s, 3, 3)).as("10+20").isEqualTo(30);
        assertThat(num(s, 3, 4)).as("1000+2000").isEqualTo(3000);

        assertThat(str(s, 5, 0)).isEqualTo("수학 소 계");
        assertThat(num(s, 5, 4)).isEqualTo(500);

        assertThat(str(s, 6, 0)).isEqualTo("총 계");
        assertThat(num(s, 6, 3)).as("35").isEqualTo(35);
        assertThat(num(s, 6, 4)).as("3500").isEqualTo(3500);

        // 합계행의 이름 칸은 비어야 한다 — 값이 있으면 데이터 행처럼 읽힌다
        assertThat(s.getRow(6).getCell(2)).satisfiesAnyOf(
                c -> assertThat(c).isNull(),
                c -> assertThat(c.getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.BLANK));
    }

    @Test
    @DisplayName("행이 없어도 총계는 낸다 — 빈 파일과 '0으로 집계됐다'는 다른 말이다")
    void 빈_결과에도_총계() throws Exception {
        Sheet s = sheetOf(excel.toXlsx("시트", COLS, List.of(), null,
                new Subtotal("catCode", "catName", List.of("qty", "amount"))));

        assertThat(str(s, 1, 0)).isEqualTo("총 계");
        assertThat(num(s, 1, 3)).isZero();
    }

    @Test
    @DisplayName("실제 export에 붙어 있다 — 수불부·순매출·수익신고")
    void 실제_export에_적용됨() throws Exception {
        assertThat(str(sheetOf(getBytes(
                "/stock/ledger/export?fromDate=2026-01-01&toDate=2026-06-30").getBody()), 0, 0))
                .isEqualTo("제품수불부현황");
        assertThat(str(sheetOf(getBytes(
                "/sales/net-summary/export?fromDate=2026-01-01&toDate=2026-06-30").getBody()), 0, 0))
                .isEqualTo("순매출조회");
        assertThat(str(sheetOf(getBytes(
                "/closing/revenue-report/export?fromDate=2026-01-01&toDate=2026-06-30").getBody()), 0, 0))
                .isEqualTo("수익신고");
    }
}
