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
     * 컬럼 스펙 지정 xlsx(드라이브 재무파일 형식 대응). 헤더=한글, 값=행의 field 추출.
     * 근거: '엑셀 형식은 드라이브 파일 참고'(재무팀).
     */
    public byte[] toXlsx(String sheetName, List<Col> cols, List<?> rows) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (Object r : rows) {
            maps.add(om.convertValue(r, MAP_TYPE));
        }
        List<String> headers = cols.stream().map(Col::header).toList();
        return write(sheetName, headers, maps.stream()
                .map(m -> cols.stream().map(c -> m.get(c.field())).toList())
                .toList());
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
                .toList());
    }

    /** 헤더 + 값 행렬 → xlsx byte[](공용 엔진). */
    private byte[] write(String sheetName, List<String> headers, List<List<Object>> rows) {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet((sheetName == null || sheetName.isBlank()) ? "Sheet1" : sheetName);

            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int c = 0; c < headers.size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
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
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(cd);
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        return ResponseEntity.ok().headers(headers).body(xlsx);
    }
}
