package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 거래처별 매출대비표(당해 vs 전년 동기간). 근거: 레거시 거래처별_매출대비표.vb.
 * 레거시는 catCode 내 인코딩된 연도로 전년을 유도하나, 우리 catCode엔 연도가 없어
 * salesDate 기준 전년 동기간([from−1년, to−1년])으로 비교(대시보드 전년비와 동일 방식).
 */
public record YoyComparisonResponse(
        @Schema(description = "당해 시작일") LocalDate from,
        @Schema(description = "당해 종료일") LocalDate to,
        @Schema(description = "전년 시작일") LocalDate prevFrom,
        @Schema(description = "전년 종료일") LocalDate prevTo,
        @Schema(description = "집계 단위") GroupBy groupBy,
        @Schema(description = "행 목록") List<Row> rows
) {
    /** 집계 단위: 거래처 / 거래처×분류 / 거래처×도서. */
    public enum GroupBy {
        /** 거래처 총계 */ PARTNER,
        /** 거래처×분류 */ CATEGORY,
        /** 거래처×도서 */ BOOK
    }

    /**
     * 대비 한 행. 비율(%)=당해÷전년×100(레거시 index, 100=전년동일, 전년0이면 null).
     * cat/book 필드는 groupBy에 따라 채워짐.
     */
    public record Row(
            @Schema(description = "거래처 id") Long partnerId,
            @Schema(description = "거래처코드") String partnerCode,
            @Schema(description = "거래처명") String partnerName,
            @Schema(description = "분류코드(CATEGORY/BOOK)") String catCode,
            @Schema(description = "분류명") String catName,
            @Schema(description = "도서코드(BOOK)") String bookCode,
            @Schema(description = "도서명") String bookName,
            @Schema(description = "당해 수량") long curQty,
            @Schema(description = "당해 금액") long curAmount,
            @Schema(description = "전년 수량") long prevQty,
            @Schema(description = "전년 금액") long prevAmount,
            @Schema(description = "수량 증감(당해−전년)") long diffQty,
            @Schema(description = "금액 증감(당해−전년)") long diffAmount,
            @Schema(description = "수량 비율(%, 당해÷전년×100)") Double qtyRatioPct,
            @Schema(description = "금액 비율(%, 당해÷전년×100)") Double amountRatioPct
    ) {
    }
}
