package com.daesung.sales.closing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 계산서신고 데이터(홈택스 계산서 목록). 거래처×과세구분 단위 1장.
 * 근거: 레거시 세금계산신고.vb. 실제 홈택스 파일 export는 별도 엔드포인트.
 */
public record TaxInvoiceResponse(
        @Schema(description = """
                작성일자 기본값(조회 종료일=말일). **계산서별로 다를 수 있다** —
                보정(B-6)한 건은 각 계산서의 `issueDate` 를 봐야 한다.""")
        LocalDate issueDate,
        @Schema(description = "계산서 목록") List<Invoice> invoices,
        @Schema(description = "계산서 매수") int count
) {
    public record Invoice(
            @Schema(description = """
                    이 계산서의 **작성일자**. 보정이 없으면 조회 종료일과 같고,
                    수정했으면 그 날짜다(현장 피드백 2026-10-14 B-6).
                    ★엑셀도 이 값으로 나간다.""")
            LocalDate issueDate,
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
            @Schema(description = "합계 검증(품목 합 == 총계)") boolean balanced,

            @Schema(description = """
                    공급받는자 사업장주소(보정 가능). 보정이 없으면 거래처 마스터 값.""")
            String partnerAddress,
            @Schema(description = "공급받는자 업태(보정 가능)") String partnerBizStatus,
            @Schema(description = "공급받는자 종목(보정 가능)") String partnerBizItem,
            @Schema(description = "공급받는자 이메일1(보정 가능)") String partnerEmail1,
            @Schema(description = "공급받는자 이메일2(보정 가능)") String partnerEmail2,
            @Schema(description = "영수(01)/청구(02). 보정이 없으면 02") String receiptType,
            @Schema(description = "비고(보정)") String memo,

            @Schema(description = """
                    **수기 보정이 걸려 있는가**(발주처 1차회신 #24).
                    true 면 위 값 중 일부가 매출에서 파생한 값이 아니라 담당자가 넣은 값이다.""")
            boolean adjusted,
            @Schema(description = "보정 사유(있으면)") String adjustReason,
            @Schema(description = """
                    **보정 전 공급가액**. 금액을 보정했을 때만 값이 있다.
                    ‼️장부와 다르다는 사실을 숨기지 않으려고 함께 내린다 —
                    없으면 나중에 세무 소명 때 아무도 설명하지 못한다.""")
            Long originalSupplyTotal,
            @Schema(description = "보정 전 세액(금액을 보정했을 때만)") Long originalTaxTotal
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
            Long unitPrice,

            @Schema(description = """
                    분류코드(2026-10-08 추가). 품목을 **상품군으로 묶는 축**이다.
                    ‼️'상품군 4종'의 정의를 아직 못 받아 서버가 묶지 않는다 —
                    임의 기준으로 묶으면 신고 숫자가 틀어지므로 묶을 수 있는 값만 내려준다.""")
            String catCode,
            @Schema(description = "분류명") String catName,
            @Schema(description = "세부구분(모의고사/교재/기타고사/특강/기타). 우리가 가진 분류는 5종이다")
            String salesDivision
    ) {
    }
}
