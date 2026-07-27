package com.daesung.sales.closing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 계산서·세금계산서 월별신고(38p). 발행유형(계산서=면세/세금계산서=과세) 기준 세무 관점 집계.
 * 근거: 재무팀 파일 '2026 월별 신고내역_순매출조회'. 발행유형은 sale.tax(0/≠0)로 파생(신규 필드 없음).
 * ⚠️ '미발행분'은 발주처 정의 미확정 → 현재 0 placeholder(정의 확정 시 채움).
 */
public record TaxFilingResponse(
        @Schema(description = "신고연도", example = "2026") int year,
        @Schema(description = "월별(1~12) 행") List<MonthRow> rows,
        @Schema(description = "연간 합계") MonthRow total
) {
    public record MonthRow(
            @Schema(description = "월(1~12), 합계행은 0", example = "6") int month,
            @Schema(description = "계산서 매출액(면세)") long invoiceSale,
            @Schema(description = "계산서 미발행분(정의 미확정, 현재 0)") long invoiceUnissued,
            @Schema(description = "세금계산서 매출액(과세)") long taxInvoiceSale,
            @Schema(description = "세금계산서 미발행분(정의 미확정, 현재 0)") long taxInvoiceUnissued,
            @Schema(description = "계산서 반품액") long invoiceReturn,
            @Schema(description = "세금계산서 반품액") long taxInvoiceReturn,
            @Schema(description = "순매출 계산서(매출−반품)") long invoiceNet,
            @Schema(description = "순매출 세금계산서(매출−반품)") long taxInvoiceNet,
            @Schema(description = "계(순매출 합)") long netTotal,
            @Schema(description = "세액(반품 차감 순액)") long tax
    ) {
    }
}
