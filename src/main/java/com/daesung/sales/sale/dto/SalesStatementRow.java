package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 매출액명세서 한 행. 근거: 레거시 매출액명세서.vb rollup(left(catCode,1), catCode, bookCode).
 * rowType으로 계층 표시(총계/대분류계/분류소계/도서상세). 합계=금액+세액(레거시 totalAmt2 미사용 규칙).
 */
public record SalesStatementRow(
        @Schema(description = "행 구분", allowableValues = {"GRAND_TOTAL", "MAJOR_TOTAL", "CAT_SUBTOTAL", "DETAIL"})
        RowType rowType,
        @Schema(description = "대분류코드(catCode 첫 글자)") String majorCode,
        @Schema(description = "분류코드(catCode)") String catCode,
        @Schema(description = "분류명") String catName,
        @Schema(description = "도서코드(상세행만)") String bookCode,
        @Schema(description = "도서명(상세행만)") String bookName,
        @Schema(description = "수량") long qty,
        @Schema(description = "금액(공급가 합)") long amount,
        @Schema(description = "세액 합") long tax,
        @Schema(description = "합계(금액+세액)") long total
) {
    public enum RowType {
        /** 총 계 */ GRAND_TOTAL,
        /** 분류 계(대분류=catCode 첫 글자) */ MAJOR_TOTAL,
        /** 소 계(분류=catCode) */ CAT_SUBTOTAL,
        /** 도서 상세 */ DETAIL
    }

    public static SalesStatementRow detail(String majorCode, String catCode, String catName,
                                           String bookCode, String bookName, long qty, long amount, long tax) {
        return new SalesStatementRow(RowType.DETAIL, majorCode, catCode, catName,
                bookCode, bookName, qty, amount, tax, amount + tax);
    }

    public static SalesStatementRow catSubtotal(String majorCode, String catCode, String catName,
                                                long qty, long amount, long tax) {
        return new SalesStatementRow(RowType.CAT_SUBTOTAL, majorCode, catCode, catName,
                null, null, qty, amount, tax, amount + tax);
    }

    public static SalesStatementRow majorTotal(String majorCode, long qty, long amount, long tax) {
        return new SalesStatementRow(RowType.MAJOR_TOTAL, majorCode, null, null,
                null, null, qty, amount, tax, amount + tax);
    }

    public static SalesStatementRow grandTotal(long qty, long amount, long tax) {
        return new SalesStatementRow(RowType.GRAND_TOTAL, null, null, null,
                null, null, qty, amount, tax, amount + tax);
    }
}
