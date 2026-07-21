package com.daesung.sales.receivable.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 외상매출장(단일 거래처 상세 러닝밸런스). 근거: 레거시 외상매출장조회.
 * 기초이월 + 기간 내 매출/반품/수금 명세 + 일자별 누계. 누계 = 이월 + Σ(채권 증감).
 */
public record ArLedgerResponse(
        @Schema(description = "거래처 id") Long partnerId,
        @Schema(description = "거래처명") String partnerName,
        @Schema(description = "조회 시작일") LocalDate fromDate,
        @Schema(description = "조회 종료일") LocalDate toDate,
        @Schema(description = "기초이월(시작일 직전까지 잔액)") long opening,
        @Schema(description = "기말잔액(누계 마지막)") long closing,
        @Schema(description = "명세 라인(일자순)") List<Line> lines
) {
    public record Line(
            @Schema(description = "거래일자") LocalDate date,
            @Schema(description = "구분(매출/교사용/증정/반품/수금)") String kind,
            @Schema(description = "전표번호(매출번호 또는 수금번호)") String refNo,
            @Schema(description = "적요(도서 또는 수금유형)") String description,
            @Schema(description = "채권 증감(매출+, 반품·수금−)") long amount,
            @Schema(description = "누계(러닝밸런스)") long balance
    ) {
    }
}
