package com.daesung.sales.closing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 계산서신고 데이터(홈택스 계산서 목록). 거래처×과세구분 단위 1장.
 * 근거: 레거시 세금계산신고.vb. 실제 홈택스 파일 export는 별도 엔드포인트.
 */
public record TaxInvoiceResponse(
        @Schema(description = "작성일자(조회 종료일=말일)") LocalDate issueDate,
        @Schema(description = "계산서 목록") List<Invoice> invoices,
        @Schema(description = "계산서 매수") int count
) {
    public record Invoice(
            @Schema(description = "종류코드(05=면세/01=과세)") String typeCode,
            @Schema(description = "과세구분(FREE/TAXABLE)") String taxType,
            @Schema(description = "공급자 상호(설정 주입, 미설정 시 공란)") String supplierName,
            @Schema(description = "공급자 사업자번호") String supplierBizNo,
            @Schema(description = "공급받는자 거래처 id") Long partnerId,
            @Schema(description = "공급받는자 상호") String partnerName,
            @Schema(description = "공급받는자 사업자번호") String partnerBizNo,
            @Schema(description = "공급받는자 대표자") String partnerBossName,
            @Schema(description = "품목(도서별)") List<Item> items,
            @Schema(description = "공급가액 합계") long supplyTotal,
            @Schema(description = "세액 합계") long taxTotal,
            @Schema(description = "합계 검증(품목 합 == 총계)") boolean balanced
    ) {
    }

    @Schema(name = "TaxInvoiceItem")

    public record Item(
            @Schema(description = "품목(도서명)") String name,
            @Schema(description = "공급가액") long supply,
            @Schema(description = "세액") long tax
    ) {
    }
}
