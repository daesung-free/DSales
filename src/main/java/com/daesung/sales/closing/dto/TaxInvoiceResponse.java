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
            @Schema(description = "세액") long tax,
            @Schema(description = """
                    수량(반품은 음수로 상계). 홈택스 양식의 '수량N' 칸 —
                    9/27 회의 항목 16 "기존 첨부 홈택스 양식은 더 상세함".""")
            long qty,
            @Schema(description = """
                    단가. 홈택스 양식의 '단가N' 칸.
                    ‼️**한 품목이 여러 매출 라인의 합**이라 단가가 라인마다 다르면 하나로 정할 수 없다.
                    그럴 때는 <b>비운다</b>(null) — 아무 값이나 넣으면 단가×수량이 공급가액과
                    어긋나 신고 파일이 틀린 것으로 보인다. 홈택스에서 단가는 선택 항목이다.""")
            Long unitPrice
    ) {
    }
}
