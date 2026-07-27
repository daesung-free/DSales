package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 월별매출액명세서(37p). 근거: 재무팀 파일 '연구소 월별매출액 명세서'.
 * 구분(대분류)×상품별 성적처리/비처리 인원·금액 + 계 + 과세매출액 + 부가세. 대분류 소계·총계 포함.
 * 인원 = 매출 수량(모의고사는 응시인원), 금액 = 공급가액. 매출(SALE)만 집계(무상·반품 제외).
 * ⚠️ 성적처리 구분은 sale.proc_type(null=비처리). DSRE 모의고사 자동 인원/처리 import는 후속.
 */
public record MonthlyStatementResponse(
        @Schema(description = "연도", example = "2026") int year,
        @Schema(description = "월", example = "6") int month,
        @Schema(description = "명세 행(상세/대분류계/총계)") List<Row> rows
) {
    public record Row(
            @Schema(description = "행 구분", allowableValues = {"GRAND_TOTAL", "MAJOR_SUBTOTAL", "DETAIL"})
            RowType rowType,
            @Schema(description = "대분류코드(cat_code 첫 글자)") String majorCode,
            @Schema(description = "분류명(상세행)") String catName,
            @Schema(description = "도서코드(상세행)") String bookCode,
            @Schema(description = "도서명(상세행)") String bookName,
            @Schema(description = "성적처리 인원") long gradedQty,
            @Schema(description = "성적처리 금액(공급가)") long gradedAmount,
            @Schema(description = "비처리 인원") long ungradedQty,
            @Schema(description = "비처리 금액(공급가)") long ungradedAmount,
            @Schema(description = "계 인원(성적처리+비처리)") long totalQty,
            @Schema(description = "계 금액(공급가)") long totalAmount,
            @Schema(description = "과세매출액(과세분 공급가)") long taxableAmount,
            @Schema(description = "부가세") long vat
    ) {
        public enum RowType {
            /** 총 계 */ GRAND_TOTAL,
            /** 대분류 계 */ MAJOR_SUBTOTAL,
            /** 상품 상세 */ DETAIL
        }
    }
}
