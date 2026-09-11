package com.daesung.sales.common.excel;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * 범용 엑셀(xlsx) 익스포터. 리포트 행(record/DTO) 리스트를 Jackson으로 평탄화 → 필드명 헤더 + 값 셀.
 * 아마란스(회계ERP) 자동연동 대신 '모든 항목 엑셀 다운로드'(수동 업로드) 방침 대응. 근거: 재무팀 확정.
 * 중첩 객체/배열 값은 JSON 문자열로 기록(대부분 리포트 행은 평면 구조).
 */
@Component
@RequiredArgsConstructor
public class ExcelExportUtil {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper om;

    /** 컬럼 스펙: 드라이브 샘플 형식의 한글 헤더 + DTO 필드명. */
    public record Col(String header, String field) {
    }

    /**
     * 제목·조회기준 머리글(2행). 근거: 재무팀 실파일이 전부
     * <b>제목 / 조회기준 / 헤더 / 데이터</b> 구조이고, 프론트도 같은 2행을 붙이고 있다
     * (백엔드 전달 2026-08-20 §A-2 — 서버 export로 일원화하면 이 2행이 사라진다는 지적).
     *
     * <p>재무팀은 <b>기존 파일과 눈으로 대조</b>하는 환경이라 형식 차이가 곧 클레임이다.
     *
     * @param title    1행: 화면 제목(예: 외상매출현황조회)
     * @param criteria 2행: 조회기준(예: {@code 조회기준 : 2026.01.01 ~ 2026.06.30}). null이면 1행만
     */
    public record Heading(String title, String criteria) {

        /** 기간 조회 화면의 표준 문구. 날짜 표기는 재무팀 파일과 같은 점 구분이다. */
        public static Heading period(String title, java.time.LocalDate from, java.time.LocalDate to) {
            return new Heading(title, "조회기준 : " + dot(from) + " ~ " + dot(to));
        }

        /** 기준일 하나짜리 화면(잔액·현황 등). */
        public static Heading asOf(String title, java.time.LocalDate baseDate) {
            return new Heading(title, "조회기준 : " + dot(baseDate));
        }

        private static String dot(java.time.LocalDate d) {
            return (d == null) ? "" : d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd"));
        }
    }

    /**
     * 분류별 소계 스펙. 근거: 재무팀 실파일(매출액정리_6월.xlsx)이 분류별 '소 계'와 총계를 포함하고,
     * 레거시 {@code 매출액명세서.vb}도 ROLLUP으로 같은 행을 만든다.
     *
     * <p>★<b>정렬된 순서에서 값이 바뀌는 지점</b>에 소계가 들어간다. 그래서 rows가
     * groupBy 기준으로 정렬돼 있어야 한다 — 흩어져 있으면 같은 분류의 소계가 여러 번 찍힌다.
     *
     * @param groupBy  묶음 기준 필드(예: catCode)
     * @param labelKey 소계 행에 표시할 이름 필드(예: catName)
     * @param sumKeys  합계를 낼 숫자 필드들
     */
    public record Subtotal(String groupBy, String labelKey, List<String> sumKeys) {
    }

    /**
     * 필드 값 추출. 평범한 키 외에 <b>{@code 이름[인덱스]}</b> 표기를 지원한다.
     *
     * <p>월별 크로스탭(1월[수량] … 12월[수량])처럼 값이 리스트로 오는 리포트가 있는데,
     * 평면 키 조회만 하면 그 칸이 통째로 빈다(실제로 응시현황에서 빈 채로 나왔다).
     */
    private static Object resolve(Map<String, Object> row, String field) {
        int open = field.indexOf('[');
        if (open < 0 || !field.endsWith("]")) {
            return row.get(field);
        }
        Object base = row.get(field.substring(0, open));
        if (!(base instanceof List<?> list)) {
            return null;
        }
        try {
            int idx = Integer.parseInt(field.substring(open + 1, field.length() - 1));
            return (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 컬럼 스펙 지정 xlsx(드라이브 재무파일 형식 대응). 헤더=한글, 값=행의 field 추출.
     * 근거: '엑셀 형식은 드라이브 파일 참고'(재무팀).
     */
    public byte[] toXlsx(String sheetName, List<Col> cols, List<?> rows) {
        return toXlsx(sheetName, cols, rows, null, null);
    }

    /** 제목·조회기준 2행을 붙인 xlsx(재무팀 실파일 형식). */
    public byte[] toXlsx(String sheetName, List<Col> cols, List<?> rows, Heading heading) {
        return toXlsx(sheetName, cols, rows, heading, null);
    }

    /**
     * 제목 2행 + 분류별 소계·총계를 붙인 xlsx.
     *
     * <p>★소계는 <b>본문 행 사이</b>에 끼워 넣는다. 파일을 열었을 때 분류가 끝나는 자리에
     * 바로 '소 계'가 보여야 재무팀이 기존 파일과 줄을 맞춰 볼 수 있다.
     */
    public byte[] toXlsx(String sheetName, List<Col> cols, List<?> rows,
                         Heading heading, Subtotal subtotal) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (Object r : rows) {
            maps.add(om.convertValue(r, MAP_TYPE));
        }
        List<String> headers = cols.stream().map(Col::header).toList();
        List<List<Object>> body = (subtotal == null)
                ? maps.stream().map(m -> cols.stream().map(c -> resolve(m, c.field())).toList()).toList()
                : withSubtotals(cols, maps, subtotal);
        return write(sheetName, headers, body, heading);
    }

    /**
     * 본문 + 분류 소계 + 총계 행렬. 숫자 합계는 {@code sumKeys} 컬럼에만 찍고
     * 나머지 칸은 비운다 — 코드·이름 칸에 합계가 찍히면 데이터처럼 읽힌다.
     */
    private List<List<Object>> withSubtotals(List<Col> cols, List<Map<String, Object>> maps,
                                             Subtotal spec) {
        List<List<Object>> out = new ArrayList<>();
        Map<String, java.math.BigDecimal> groupSum = new java.util.LinkedHashMap<>();
        Map<String, java.math.BigDecimal> grandSum = new java.util.LinkedHashMap<>();
        Object curGroup = null;
        String curLabel = null;
        boolean open = false;

        for (Map<String, Object> m : maps) {
            Object g = m.get(spec.groupBy());
            if (open && !java.util.Objects.equals(g, curGroup)) {
                out.add(totalRow(cols, spec, curLabel + " 소 계", groupSum));
                groupSum.clear();
                open = false;
            }
            if (!open) {
                curGroup = g;
                curLabel = String.valueOf(m.getOrDefault(spec.labelKey(), ""));
                open = true;
            }
            out.add(cols.stream().map(c -> resolve(m, c.field())).toList());
            for (String k : spec.sumKeys()) {
                java.math.BigDecimal v = decimalOf(m.get(k));
                groupSum.merge(k, v, java.math.BigDecimal::add);
                grandSum.merge(k, v, java.math.BigDecimal::add);
            }
        }
        if (open) {
            out.add(totalRow(cols, spec, curLabel + " 소 계", groupSum));
        }
        // 본문이 비어도 총계는 낸다 — 빈 파일과 "0으로 집계됐다"는 다른 말이다.
        out.add(totalRow(cols, spec, "총 계", grandSum));
        return out;
    }

    /** 합계 행 한 줄. 라벨은 첫 칸에, 숫자는 해당 컬럼에만. */
    private static List<Object> totalRow(List<Col> cols, Subtotal spec, String label,
                                         Map<String, java.math.BigDecimal> sums) {
        List<Object> row = new ArrayList<>();
        for (int i = 0; i < cols.size(); i++) {
            String field = cols.get(i).field();
            if (i == 0) {
                row.add(label);
            } else if (spec.sumKeys().contains(field)) {
                java.math.BigDecimal v = sums.get(field);
                row.add((v == null) ? 0L : v);
            } else {
                row.add(null);
            }
        }
        return row;
    }

    private static java.math.BigDecimal decimalOf(Object v) {
        if (v instanceof Number n) {
            return new java.math.BigDecimal(n.toString());
        }
        return java.math.BigDecimal.ZERO;
    }

    /** 행 리스트 → xlsx byte[]. 컬럼은 각 행 필드명의 합집합(첫 행 순서 우선). 헤더=필드명. */
    public byte[] toXlsx(String sheetName, List<?> rows) {
        List<Map<String, Object>> maps = new ArrayList<>();
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        for (Object r : rows) {
            Map<String, Object> m = om.convertValue(r, MAP_TYPE);
            maps.add(m);
            columns.addAll(m.keySet());
        }
        List<String> cols = new ArrayList<>(columns);
        return write(sheetName, cols, maps.stream()
                .map(m -> cols.stream().map(m::get).toList())
                .toList(), null);
    }

    /** 헤더 + 값 행렬 → xlsx byte[](공용 엔진). */
    private byte[] write(String sheetName, List<String> headers, List<List<Object>> rows,
                         Heading heading) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet((sheetName == null || sheetName.isBlank()) ? "Sheet1" : sheetName);

            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            // 제목·조회기준 2행(있을 때만). 재무팀 실파일이 전부 이 구조라
            // 파일만 받아도 무엇을 언제 기준으로 뽑았는지 알 수 있어야 한다.
            int headerRow = 0;
            if (heading != null) {
                CellStyle titleStyle = wb.createCellStyle();
                Font titleFont = wb.createFont();
                titleFont.setBold(true);
                titleFont.setFontHeightInPoints((short) 14);
                titleStyle.setFont(titleFont);

                Cell t = sheet.createRow(headerRow++).createCell(0);
                t.setCellValue(heading.title());
                t.setCellStyle(titleStyle);
                if (heading.criteria() != null && !heading.criteria().isBlank()) {
                    sheet.createRow(headerRow++).createCell(0).setCellValue(heading.criteria());
                }
            }

            Row header = sheet.createRow(headerRow);
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = headerRow + 1;
            for (List<Object> r : rows) {
                Row row = sheet.createRow(rowIdx++);
                for (int c = 0; c < headers.size(); c++) {
                    setCell(row.createCell(c), c < r.size() ? r.get(c) : null);
                }
            }
            for (int c = 0; c < headers.size(); c++) {
                sheet.autoSizeColumn(c);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "엑셀 생성 실패: " + e.getMessage());
        }
    }

    private void setCell(Cell cell, Object v) {
        if (v == null) {
            cell.setBlank();
        } else if (v instanceof java.math.BigDecimal d) {
            cell.setCellValue(d.doubleValue());
        } else if (v instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (v instanceof Boolean b) {
            cell.setCellValue(b);
        } else if (v instanceof String s) {
            cell.setCellValue(s);
        } else {
            // 중첩 객체/배열 등은 JSON 문자열로
            try {
                cell.setCellValue(om.writeValueAsString(v));
            } catch (Exception e) {
                cell.setCellValue(String.valueOf(v));
            }
        }
    }

    /** xlsx byte[] → 첨부 다운로드 응답(파일명 UTF-8 인코딩). */
    public ResponseEntity<byte[]> asDownload(byte[] xlsx, String filename) {
        // ★헤더를 직접 만든다. ContentDisposition.filename(name, UTF_8) 은 MIME 인코딩 워드
        //   (`=?UTF-8?Q?=EA=B1=B0...?=`)로 내보내는데, 이건 메일용 형식이라 HTTP에서는
        //   브라우저마다 해석이 갈리고 접근로그에도 그 문자열이 그대로 남았다(2026-09-11 발견).
        //   RFC 6266 권장 형식인 `filename*=UTF-8''<percent>` 로 내보내고,
        //   그걸 못 읽는 구형 클라이언트를 위해 ASCII fallback `filename=` 을 함께 둔다.
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        return ResponseEntity.ok().headers(headers).body(xlsx);
    }
}
