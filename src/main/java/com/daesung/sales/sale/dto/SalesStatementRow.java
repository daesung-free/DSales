package com.daesung.sales.sale.dto;

import com.daesung.sales.product.entity.MajorCategory;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 매출액명세서 한 행. 근거: 레거시 매출액명세서.vb rollup(대분류, catCode, bookCode).
 * rowType으로 계층 표시(총계/대분류계/분류소계/도서상세). 합계=금액+세액(레거시 totalAmt2 미사용 규칙).
 *
 * <p>★대분류는 <b>세부구분 마스터에서 파생</b>된다(발주처 회신 2026-08-20).
 * 예전엔 {@code left(catCode,1)}로 코드 첫 글자를 그대로 썼는데, 화면에 'H'·'M' 같은 글자가 나왔고
 * 분류코드 체계가 갖춰지지 않은 신규 데이터에서는 대분류가 아예 엉뚱한 값이 됐다.
 * 레거시가 그 글자에 숨겨 두었던 축({@code CommonDB.vb:151~161} 'H'→교재·'M'→모의고사)을
 * 이제 필드로 들고 있으므로 추측할 필요가 없다.
 */
public record SalesStatementRow(
        @Schema(description = "행 구분", allowableValues = {"GRAND_TOTAL", "MAJOR_TOTAL", "CAT_SUBTOTAL", "DETAIL"})
        RowType rowType,
        @Schema(description = "대분류 코드(세부구분 마스터에서 파생). 미지정 상품은 null") MajorCategory majorCategory,
        @Schema(description = "대분류 명칭(모의고사·교재 …). 미지정은 '미분류'") String majorName,
        @Schema(description = "분류코드(catCode)") String catCode,
        @Schema(description = "분류명") String catName,
        @Schema(description = "도서코드(상세행만)") String bookCode,
        @Schema(description = "도서명(상세행만)") String bookName,
        @Schema(description = "수량") long qty,
        @Schema(description = "금액(공급가 합)") long amount,
        @Schema(description = "세액 합") long tax,
        @Schema(description = "합계(금액+세액)") long total
) {
    /** 대분류가 없는 상품(세부구분 미지정)도 집계에서 빠지면 안 되므로 이 이름으로 모은다. */
    public static final String UNCLASSIFIED = "미분류";

    public enum RowType {
        /** 총 계 */ GRAND_TOTAL,
        /** 분류 계(대분류 단위) */ MAJOR_TOTAL,
        /** 소 계(분류=catCode) */ CAT_SUBTOTAL,
        /** 도서 상세 */ DETAIL
    }

    private static String nameOf(MajorCategory c) {
        return (c == null) ? UNCLASSIFIED : c.label();
    }

    public static SalesStatementRow detail(MajorCategory major, String catCode, String catName,
                                           String bookCode, String bookName, long qty, long amount, long tax) {
        return new SalesStatementRow(RowType.DETAIL, major, nameOf(major), catCode, catName,
                bookCode, bookName, qty, amount, tax, amount + tax);
    }

    public static SalesStatementRow catSubtotal(MajorCategory major, String catCode, String catName,
                                                long qty, long amount, long tax) {
        return new SalesStatementRow(RowType.CAT_SUBTOTAL, major, nameOf(major), catCode, catName,
                null, null, qty, amount, tax, amount + tax);
    }

    public static SalesStatementRow majorTotal(MajorCategory major, long qty, long amount, long tax) {
        return new SalesStatementRow(RowType.MAJOR_TOTAL, major, nameOf(major), null, null,
                null, null, qty, amount, tax, amount + tax);
    }

    public static SalesStatementRow grandTotal(long qty, long amount, long tax) {
        return new SalesStatementRow(RowType.GRAND_TOTAL, null, null, null, null,
                null, null, qty, amount, tax, amount + tax);
    }
}
