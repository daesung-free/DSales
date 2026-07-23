package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 거래명세서 데이터. 근거: 레거시 UC_TabPages_ETC.vb RunCurrentReport(거래명세서_공급자.rdlc).
 * 공급자(자사)·공급받는자(거래처) + 유가/무가 라인 분리 + 합계.
 * 공급자는 단일법인 가정(SupplierProperties) — 법인축(멀티테넌트) 확정 시 확장. [[dsre2-stays-existing]] 무관.
 */
public record TransactionStatementResponse(
        @Schema(description = "조회 시작일") LocalDate from,
        @Schema(description = "조회 종료일") LocalDate to,
        @Schema(description = "회계구분 필터(null=매출+무가)") String category,
        @Schema(description = "공급자(자사)") Party provider,
        @Schema(description = "공급받는자(거래처)") Party receiver,
        @Schema(description = "유가 품목(공급가액>0)") List<Line> pricedLines,
        @Schema(description = "무가 품목(교사용/증정 등, 공급가액=0)") List<Line> freeLines,
        @Schema(description = "합계") Totals totals
) {
    /** 거래 당사자(공급자/공급받는자 공통). */
    public record Party(
            @Schema(description = "코드(거래처만)") String code,
            @Schema(description = "상호") String name,
            @Schema(description = "사업자번호") String bizNo,
            @Schema(description = "대표자") String bossName,
            @Schema(description = "주소") String address,
            @Schema(description = "업태") String bizStatus,
            @Schema(description = "종목") String bizItem
    ) {
    }

    /** 명세 라인. bookLabel=분류명/도서명. */
    public record Line(
            @Schema(description = "순번") int seq,
            @Schema(description = "도서표기(분류명 / 도서명)") String bookLabel,
            @Schema(description = "도서코드") String bookCode,
            @Schema(description = "수량") int qty,
            @Schema(description = "정가") Integer listPrice,
            @Schema(description = "공급률(%)") Integer supplyRate,
            @Schema(description = "공급단가(공급가액/수량)") long unitSupplyPrice,
            @Schema(description = "공급가액") long supplyAmount,
            @Schema(description = "세액") long tax,
            @Schema(description = "회계구분") String category,
            @Schema(description = "비고") String memo
    ) {
    }

    /** 합계(유가 기준 금액, 무가는 수량만 별도). */
    public record Totals(
            @Schema(description = "유가 수량 합") long pricedQty,
            @Schema(description = "공급가액 합") long supplyAmount,
            @Schema(description = "세액 합") long tax,
            @Schema(description = "합계(공급가액+세액)") long total,
            @Schema(description = "무가 수량 합") long freeQty
    ) {
    }
}
