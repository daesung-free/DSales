package com.daesung.sales.closing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 계산서 반품/취소 조정 명세(10일 분기). 근거: 재무팀 확정 2026-07-25.
 * 반품 처리일이 매월 10일(발행기준일) 이전=당월 수정발행(AMEND) / 이후=익월 정산 마이너스(NEXT_MONTH_MINUS).
 * ⚠️'반영 신고월'은 당월/익월 기준(원계산서가 전월분인지 등 세부는 발주처 확인 대상).
 */
public record InvoiceAdjustmentResponse(
        @Schema(description = "조회 반품 발생 연도") int year,
        @Schema(description = "조회 반품 발생 월") int month,
        @Schema(description = "반품 건별 조정 분류") List<Row> rows,
        @Schema(description = "합계(방식별)") Summary summary
) {
    /** 조정 방식. */
    public enum Mode {
        /** 수정발행: 10일 이전 반품 → 당월 신고 원계산서 수정. */
        AMEND,
        /** 익월 마이너스: 10일 이후 반품 → 당월 마감됨 → 익월 신고에 마이너스 반영. */
        NEXT_MONTH_MINUS
    }

    @Schema(name = "InvoiceAdjustmentRow")

    public record Row(
            @Schema(description = "매출(반품)번호") String salesNo,
            @Schema(description = "거래처명") String partnerName,
            @Schema(description = "도서명") String productName,
            @Schema(description = "반품 처리일") LocalDate returnDate,
            @Schema(description = "공급가액(반품, 양수)") long supplyAmount,
            @Schema(description = "세액") long tax,
            @Schema(description = "조정 방식(AMEND/NEXT_MONTH_MINUS)") Mode mode,
            @Schema(description = "반영 신고월(yyyyMM)") String reportingMonth
    ) {
    }

    @Schema(name = "InvoiceAdjustmentSummary")

    public record Summary(
            @Schema(description = "당월 수정발행 공급가 합") long amendSupply,
            @Schema(description = "당월 수정발행 세액 합") long amendTax,
            @Schema(description = "익월 마이너스 공급가 합") long nextMonthSupply,
            @Schema(description = "익월 마이너스 세액 합") long nextMonthTax
    ) {
    }
}
