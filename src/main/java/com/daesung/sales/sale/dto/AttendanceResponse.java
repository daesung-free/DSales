package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 응시현황(연도별) — 거래처별 <b>월별 수량·매출 크로스탭</b>.
 *
 * <p>이름이 '응시현황'이라 시험 신청·응시율 표로 오해하기 쉬운데, 레거시 응시현황.vb 실물은
 * {@code 1월[수량]·1월[매출] … 12월[수량]·12월[매출] + 합계} 구성이다.
 * 신청·응시율 같은 항목은 레거시 어디에도 없다.
 *
 * <p>행은 레거시 {@code GROUP BY rollup(지역구분, custCode)}에 맞춰 3단으로 나온다 —
 * 거래처 → 지역구분 소계 → 총계.
 */
@Schema(name = "AttendanceResponse", description = "응시현황(연도별) 거래처×월 크로스탭")
public record AttendanceResponse(
        @Schema(description = "조회 연도") int year,
        @Schema(description = "행 목록(거래처 → 지역구분 소계 → 총계)") List<Row> rows
) {
    @Schema(name = "AttendanceRow")
    public record Row(
            @Schema(description = "행 종류 PARTNER(거래처)/REGION_SUBTOTAL(지역구분 소계)/TOTAL(총계)")
            String rowType,
            @Schema(description = "지역구분", example = "특약점_1서울") String regionGroup,
            @Schema(description = "거래처코드") String partnerCode,
            @Schema(description = "거래처명") String partnerName,
            @Schema(description = "지역(도시명)") String cityName,
            @Schema(description = "월별 수량 1~12월") List<Long> monthlyQty,
            @Schema(description = "월별 매출 1~12월") List<Long> monthlyAmount,
            @Schema(description = "합계 수량") long totalQty,
            @Schema(description = "합계 매출") long totalAmount
    ) {
    }
}
