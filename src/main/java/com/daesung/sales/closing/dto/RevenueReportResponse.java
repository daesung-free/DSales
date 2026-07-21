package com.daesung.sales.closing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/** 수익신고. 거래처별 월 순매출/세액 집계. 근거: 레거시 수익신고.vb(거래처×월). */
public record RevenueReportResponse(
        @Schema(description = "집계 시작일") LocalDate fromDate,
        @Schema(description = "집계 종료일") LocalDate toDate,
        @Schema(description = "과세구분(ALL/FREE/TAXABLE)") String taxType,
        @Schema(description = "거래처별 집계") List<PartnerRevenue> rows,
        @Schema(description = "전체 합계") PartnerRevenue total
) {
    /** 거래처 단위 집계(월별 분해 포함). */
    public record PartnerRevenue(
            @Schema(description = "거래처 id(합계행 null)") Long partnerId,
            @Schema(description = "거래처명") String partnerName,
            @Schema(description = "총 건수") long totalCount,
            @Schema(description = "총 순매출액(공급가, 반품 차감)") long totalNetSupply,
            @Schema(description = "총 세액(반품 차감)") long totalNetTax,
            @Schema(description = "월별 분해") List<MonthEntry> months
    ) {
    }

    public record MonthEntry(
            @Schema(description = "연월(yyyyMM)", example = "202606") String yearMonth,
            @Schema(description = "건수") long count,
            @Schema(description = "순매출액") long netSupply,
            @Schema(description = "세액") long netTax
    ) {
    }
}
