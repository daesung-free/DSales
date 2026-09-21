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
    /**
     * 거래처 단위 집계(월별 분해 포함).
     *
     * <p>★거래처코드·사업자번호·대표자는 <b>거래처 마스터에서 그대로</b> 실어 준다.
     * 화면이 이 셋을 컬럼으로 두고 있는데 응답에 없어서 계속 빈칸이었다(프론트 지적 2026-09-21).
     * 신고에 쓰는 표라 사업자번호·대표자가 비면 담당자가 거래처관리를 따로 열어 옮겨 적어야 한다.
     */
    public record PartnerRevenue(
            @Schema(description = "거래처 id(합계행 null)") Long partnerId,
            @Schema(description = "거래처코드(합계행 null)") String partnerCode,
            @Schema(description = "거래처명") String partnerName,
            @Schema(description = "사업자번호(거래처 마스터. 미등록이면 null)") String bizNo,
            @Schema(description = "대표자 성명(거래처 마스터. 미등록이면 null)") String bossName,
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
