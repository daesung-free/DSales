package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 응시현황(기간별, 18p) — 지역·거래처·학교·학년별 <b>월 크로스탭</b>(처리/비처리/계).
 *
 * <p>★<b>월 컬럼을 고정하지 않는다.</b> 정본 데이터 항목은 "월별(3~11월)"이지만 화면 이름이 '기간별'이다.
 * 3~11월로 박아두면 레거시와 같은 병에 걸린다 — 레거시 {@code 고사별처리인원.vb}는 월·학년·영역을
 * SQL에 하드코딩하고 <b>연도마다 그 SQL을 복붙</b>해서, 2022년 이후 분기를 아무도 쓰지 않자
 * "2022년까지만 조회 가능"이라는 안내와 함께 화면이 죽었다.
 * 여기서는 조회 기간이 포함하는 월을 그대로 칸으로 만들고, 그 목록을 {@link #months}에 실어 보낸다.
 *
 * <p>행은 학교 → 거래처 소계 → 지역 소계 → 총계 순으로 나온다(레거시 rollup과 같은 순서).
 */
@Schema(name = "AttendancePeriodResponse", description = "응시현황(기간별) 월 크로스탭")
public record AttendancePeriodResponse(
        @Schema(description = "조회 시작일") LocalDate fromDate,
        @Schema(description = "조회 종료일") LocalDate toDate,
        @Schema(description = """
                월 컬럼 목록(yyyy-MM). 조회 기간에서 만들어지므로 요청마다 개수가 다르다.
                아래 monthly* 배열이 이 순서와 1:1로 대응한다.""",
                example = "[\"2026-03\",\"2026-04\"]")
        List<String> months,
        @Schema(description = "행 목록") List<Row> rows
) {
    @Schema(name = "AttendancePeriodRow")
    public record Row(
            @Schema(description = "행 종류", allowableValues = {"SCHOOL", "PARTNER_SUBTOTAL",
                    "REGION_SUBTOTAL", "TOTAL"})
            String rowType,
            @Schema(description = "지역") String region,
            @Schema(description = "거래처(특약점) 코드") String partnerCode,
            @Schema(description = "거래처(특약점)명") String partnerName,
            @Schema(description = "학교코드") String schoolCode,
            @Schema(description = "학교명") String schoolName,
            @Schema(description = "학년") String grade,
            @Schema(description = "월별 성적처리 인원(months 순서)") List<Long> monthlyGraded,
            @Schema(description = "월별 비처리 인원(months 순서)") List<Long> monthlyUngraded,
            @Schema(description = "월별 계 인원(처리+비처리)") List<Long> monthlyTotal,
            @Schema(description = "기간 성적처리 인원 합") long gradedTotal,
            @Schema(description = "기간 비처리 인원 합") long ungradedTotal,
            @Schema(description = "기간 계 인원 합") long total
    ) {
    }
}
