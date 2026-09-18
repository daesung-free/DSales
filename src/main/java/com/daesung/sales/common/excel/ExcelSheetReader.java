package com.daesung.sales.common.excel;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 * 엑셀 업로드용 <b>헤더 이름 기반</b> 시트 리더.
 * 근거: 발주처 화면검토(2026-08-31) 화면33 — "도서관리 등록 탭(기본정보/세트구성/거래처별단가)
 * 전체에 <b>엑셀 업로드</b> 기능 신설".
 *
 * <p>★<b>열 위치가 아니라 헤더 이름으로 읽는다.</b> 이 업로드가 실제로 쓰이는 방식은
 * "목록을 내려받아 고쳐서 다시 올린다"이다. 위치로 읽으면 담당자가 열 하나를 옮기거나
 * 메모용 열을 끼워 넣는 순간 <b>전혀 다른 값이 조용히 들어간다</b> — 오류도 안 나고
 * 정가 자리에 수량이 들어가는 식이다. 이름으로 읽으면 못 찾을 때 못 찾았다고 말할 수 있다.
 *
 * <p>(매출 업로드는 발주처 <b>표준양식 12컬럼</b>이 정해져 있어 위치로 읽는다 —
 * 그건 양식이 계약이라 다르다.)
 *
 * <p>헤더는 공백을 지우고 소문자로 맞춰 비교한다. 같은 뜻의 다른 표기는
 * {@link Header#of(String...)}로 여러 이름을 받아 준다.
 */
public final class ExcelSheetReader {

    /** 헤더 후보 이름들. 첫 번째가 대표 이름(오류 문구에 쓰인다). */
    public record Header(String name, List<String> aliases) {
        public static Header of(String... names) {
            return new Header(names[0], List.of(names));
        }
    }

    private final List<RowView> rows;

    private ExcelSheetReader(List<RowView> rows) {
        this.rows = rows;
    }

    public List<RowView> rows() {
        return rows;
    }

    /**
     * 첫 시트를 읽어 행 목록으로. 0행을 헤더로 본다.
     *
     * @param required 반드시 있어야 하는 헤더. 하나라도 없으면 <b>파일 전체를 거부</b>한다 —
     *                 필수 열이 빠진 파일을 행 단위 오류로 흘리면 담당자는 수백 줄의
     *                 같은 오류를 보고서야 양식이 틀렸다는 걸 알게 된다.
     */
    public static ExcelSheetReader read(MultipartFile file, List<Header> required) {
        assertReadable(file);
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "빈 파일입니다(헤더 행이 없습니다).");
            }
            Map<String, Integer> index = new HashMap<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                String h = normalize(text(headerRow.getCell(c)));
                if (h != null && !index.containsKey(h)) {
                    index.put(h, c);       // 같은 헤더가 둘이면 앞의 것을 쓴다
                }
            }
            List<String> missing = new ArrayList<>();
            for (Header h : required) {
                if (resolve(index, h) < 0) {
                    missing.add(h.name());
                }
            }
            if (!missing.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "필수 컬럼이 없습니다: " + String.join(", ", missing)
                                + " (목록 다운로드 파일의 헤더를 그대로 두고 값만 고쳐 주세요)");
            }
            List<RowView> out = new ArrayList<>();
            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row != null && !isBlank(row)) {
                    out.add(new RowView(row, index, r + 1));
                }
            }
            return new ExcelSheetReader(out);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일을 읽을 수 없습니다: " + e.getMessage());
        } catch (BusinessException e) {
            throw e;                      // 위에서 던진 우리 오류(빈 파일·필수컬럼)는 그대로 올린다
        } catch (RuntimeException e) {
            // ‼️POI 는 빈 파일·엑셀이 아닌 파일·시트 없는 파일을 **RuntimeException** 으로 던진다
            //   (EmptyFileException·NotOfficeXmlFileException 전부 IllegalArgumentException 계열).
            //   IOException 만 잡고 있어서 그대로 500 으로 나갔다(2026-09-18 지적).
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "엑셀 파일이 아니거나 열 수 없습니다. 목록 다운로드 파일을 받아 값만 고쳐 올려 주세요.");
        }
    }

    /**
     * 읽기 전 최소 확인. <b>0바이트 파일이 가장 흔하다</b> —
     * 화면에서 파일을 안 고르고 업로드를 누르거나, 저장이 덜 된 파일을 올리는 경우다.
     * 여기서 걸러야 "서버 오류"가 아니라 "빈 파일"이라고 말해 줄 수 있다.
     */
    public static void assertReadable(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "빈 파일입니다. 내용이 있는 엑셀 파일을 올려 주세요.");
        }
    }

    private static int resolve(Map<String, Integer> index, Header h) {
        for (String alias : h.aliases()) {
            Integer i = index.get(normalize(alias));
            if (i != null) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isBlank(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            if (text(row.getCell(c)) != null) {
                return false;
            }
        }
        return true;
    }

    /** 공백 제거 + 소문자. 담당자가 "도서 코드"라 써도 "도서코드"로 읽힌다. */
    private static String normalize(String s) {
        if (s == null) {
            return null;
        }
        String v = s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return v.isEmpty() ? null : v;
    }

    private static String text(Cell c) {
        if (c == null) {
            return null;
        }
        return switch (c.getCellType()) {
            case STRING -> {
                String v = c.getStringCellValue().trim();
                yield v.isEmpty() ? null : v;
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(c)) {
                    yield c.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield number(c.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            case FORMULA -> formulaText(c);
            default -> null;
        };
    }

    /**
     * 숫자 셀 → 문자열. 엑셀은 모든 수를 double로 들고 있어 도서코드 12345가 "12345.0"이 되기 쉽다.
     * {@code BigDecimal.valueOf}는 {@code Double.toString} 기반이라 0.75를 0.7499…로 벌리지 않고,
     * {@code stripTrailingZeros + toPlainString}이 12000.0을 "12000"으로, 0.75는 "0.75"로 준다.
     * (부동소수 동치비교 관용구 {@code d == Math.floor(d)}를 쓰지 않는 이유이기도 하다.)
     */
    private static String number(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return null;
        }
        return java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    private static String formulaText(Cell c) {
        try {
            String v = c.getStringCellValue().trim();
            return v.isEmpty() ? null : v;
        } catch (IllegalStateException e) {
            return number(c.getNumericCellValue());
        }
    }

    /** 행 하나. 헤더 이름으로 값을 꺼낸다. */
    public static final class RowView {
        private final Row row;
        private final Map<String, Integer> index;
        private final int rowNo;

        private RowView(Row row, Map<String, Integer> index, int rowNo) {
            this.row = row;
            this.index = index;
            this.rowNo = rowNo;
        }

        /** 엑셀 행번호(1-base). 오류 메시지에 그대로 쓸 수 있어야 담당자가 그 줄을 찾는다. */
        public int rowNo() {
            return rowNo;
        }

        public String str(Header h) {
            int i = resolve(index, h);
            return (i < 0) ? null : text(row.getCell(i));
        }

        /** 필수 문자열. 비어 있으면 어느 열이 비었는지 이름으로 알려준다. */
        public String required(Header h) {
            String v = str(h);
            if (v == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, h.name() + "이(가) 비어 있습니다");
            }
            return v;
        }

        public Integer intOrNull(Header h) {
            String v = str(h);
            if (v == null) {
                return null;
            }
            try {
                return (int) Math.round(Double.parseDouble(v.replace(",", "").replace("%", "")));
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        h.name() + "이(가) 숫자가 아닙니다: " + v);
            }
        }

        /**
         * 정수(long). 담보금액처럼 <b>int 범위를 넘을 수 있는 금액</b>에 쓴다 —
         * {@link #intOrNull}로 받으면 21억을 넘는 순간 조용히 다른 값이 된다.
         */
        public Long longOrNull(Header h) {
            String v = str(h);
            if (v == null) {
                return null;
            }
            try {
                return Math.round(Double.parseDouble(v.replace(",", "")));
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        h.name() + "이(가) 숫자가 아닙니다: " + v);
            }
        }

        public int intVal(Header h) {
            Integer v = intOrNull(h);
            if (v == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, h.name() + "이(가) 비어 있습니다");
            }
            return v;
        }

        /**
         * 예/아니오. {@code Y·예·TRUE·1·O}는 참, {@code N·아니오·FALSE·0·X}는 거짓.
         * ★알 수 없는 값은 <b>null이 아니라 오류</b>다 — "Yes"를 조용히 false로 읽으면
         * 담당자는 껐다고 생각한 적 없는 항목이 꺼진 걸 나중에 발견한다.
         */
        public Boolean boolOrNull(Header h) {
            String v = str(h);
            if (v == null) {
                return null;
            }
            String s = v.trim().toUpperCase(Locale.ROOT);
            if (List.of("Y", "예", "TRUE", "1", "O", "노출", "사용").contains(s)) {
                return Boolean.TRUE;
            }
            if (List.of("N", "아니오", "아니요", "FALSE", "0", "X", "미노출", "미사용").contains(s)) {
                return Boolean.FALSE;
            }
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    h.name() + "은(는) Y/N으로 적어 주세요(현재 값: " + v + ")");
        }

        public LocalDate dateOrNull(Header h) {
            String v = str(h);
            if (v == null) {
                return null;
            }
            try {
                return LocalDate.parse(v.replace('.', '-').replace('/', '-'));
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        h.name() + "은(는) yyyy-MM-dd 형식이어야 합니다: " + v);
            }
        }

        /** 코드값 또는 한글 라벨로 enum 찾기. 둘 다 안 맞으면 고를 수 있는 값을 알려준다. */
        public <E extends Enum<E>> E enumOrNull(Header h, Class<E> type,
                                                java.util.function.Function<E, String> labeler) {
            String v = str(h);
            if (v == null) {
                return null;
            }
            String s = normalize(v);
            for (E e : type.getEnumConstants()) {
                if (s.equals(normalize(e.name())) || s.equals(normalize(labeler.apply(e)))) {
                    return e;
                }
            }
            List<String> choices = new ArrayList<>();
            for (E e : type.getEnumConstants()) {
                choices.add(labeler.apply(e));
            }
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    h.name() + " 값을 알 수 없습니다: " + v + " (가능: " + String.join("/", choices) + ")");
        }
    }
}
