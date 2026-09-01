package com.daesung.sales.school.dto;

import com.daesung.sales.school.entity.School;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 학교/학원검색(29p) 한 줄. 컬럼은 레거시 {@code 학교검색.vb} SQL 별칭 원문 그대로다.
 *
 * <p>‼️레거시는 <b>특약점(모의고사)·특약점(IC)를 주석 처리해 화면에 안 띄운다</b>
 * (학교검색.vb:44-45). 대신 <b>검색 조건에는 살아 있다</b> —
 * 특약점명으로 찾을 때 세 축을 다 뒤진다.
 * 여기서는 응답에 담아만 둔다. 화면에 띄울지는 발주처가 정할 일이고,
 * 빼 두면 나중에 필요할 때 서버부터 다시 고쳐야 한다.
 */
public record SchoolSearchRow(

        @Schema(description = "학교코드") String schoolCode,
        @Schema(description = "학교/학원명") String schoolName,
        @Schema(description = "지역코드") String cityCode,
        @Schema(description = "지역명") String cityName,
        @Schema(description = "특약점L — 특약점 소재") String partnerLoc,
        @Schema(description = "특약점N — 특약점명") String partnerName,
        @Schema(description = "특약점LN — '소재 + 특약점명'을 이어 붙인 표기", example = "강남 하람도서")
        String partnerLocName,
        @Schema(description = """
                특약점(모의고사). ★같은 학교라도 상품군에 따라 담당이 다르다.
                레거시는 화면에 안 띄우지만 검색 조건에는 쓴다.""")
        String mockPartnerName,
        @Schema(description = "특약점(IC). 모의고사와 같은 이유로 따로 든다") String icPartnerName,
        @Schema(description = "학교/학원 구분") String clientCategory,
        @Schema(description = "비고") String memo
) {
    public static SchoolSearchRow from(School s) {
        String loc = blankToNull(s.getPartnerLoc());
        String name = blankToNull(s.getCustName());
        String locName = (loc == null) ? name : (name == null ? loc : loc + " " + name);
        return new SchoolSearchRow(
                s.getSchoolCode(), s.getSchoolName(), s.getCityCode(), s.getCity(),
                loc, name, locName,
                s.getMockPartnerName(), s.getIcPartnerName(),
                s.getClientCategory(), s.getMemo());
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
}
