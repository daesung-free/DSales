package com.daesung.sales.closing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 계산서·세금계산서 월별신고(38p). 발행유형(계산서=면세/세금계산서=과세/미발행) 기준 세무 관점 집계.
 * 근거: 재무팀 파일 '2026 월별 신고내역_순매출조회'. 발행유형은 sale.tax(0/≠0)로 파생(신규 필드 없음).
 *
 * <p>★'미발행분' = <b>사업자번호가 없는 거래처의 매출</b>. 근거는 재무팀 양식 <b>E4 셀 메모</b> —
 * "학원매출+외부판매 / 온라인몰 / (사업자번호가없는 거래처의 합)". 계산서를 끊을 상대가 없어
 * 발행 자체를 하지 않는 건이므로 <b>계산서·세금계산서와 배타</b>다(셋을 더하면 전체 매출).
 * ‼️예전에 0 placeholder로 두었는데, 그건 "미발행이 한 건도 없다"고 신고하는 것과 같았다.
 */
public record TaxFilingResponse(
        @Schema(description = "신고연도", example = "2026") int year,
        @Schema(description = "월별(1~12) 행") List<MonthRow> rows,
        @Schema(description = "연간 합계") MonthRow total
) {
    public record MonthRow(
            @Schema(description = "월(1~12), 합계행은 0", example = "6") int month,
            @Schema(description = "매출 — 계산서(면세)") long invoiceSale,
            @Schema(description = "매출 — 세금계산서(과세)") long taxInvoiceSale,
            @Schema(description = "매출 — 총액(계산서+세금계산서+미발행분)") long saleTotal,
            @Schema(description = "매출 — 미발행분(사업자번호 없는 거래처)") long unissuedSale,
            @Schema(description = "반품 — 계산서") long invoiceReturn,
            @Schema(description = "반품 — 세금계산서") long taxInvoiceReturn,
            @Schema(description = "반품 — 미발행분") long unissuedReturn,
            @Schema(description = "순매출 — 계산서(매출−반품)") long invoiceNet,
            @Schema(description = "순매출 — 세금계산서(매출−반품)") long taxInvoiceNet,
            @Schema(description = "순매출 — 미발행분(매출−반품)") long unissuedNet,
            @Schema(description = "계(순매출 합)") long netTotal,
            @Schema(description = "세액(반품 차감 순액)") long tax
    ) {
    }
}
