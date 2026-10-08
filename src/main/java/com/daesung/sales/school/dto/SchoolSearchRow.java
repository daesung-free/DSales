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
        @Schema(description = """
                지역명(관할). 발주처 1차회신 #18 확정 7컬럼 중 하나다 —
                **도시명과 다른 축**이라 따로 둔다(예: 지역=서울관할 / 도시=강남).""")
        String regionName,
        @Schema(description = "도시명") String cityName,
        @Schema(description = """
                거래처명1(상호). 발주처 #18 확정 컬럼. 학교 마스터엔 거래처명이 한 칸뿐이라
                **거래처 마스터에서 가져온다**(학교의 거래처코드로 찾는다).
                거래처를 못 찾으면 null — 막지 않는다(마스터에 아직 없는 거래처가 실제로 있다).""")
        String partnerName1,
        @Schema(description = """
                거래처명2(풀네임). 거래처 마스터 값이 있으면 그것을, 없으면
                학교에 적힌 거래처명을 쓴다 — 예전 데이터도 빈칸으로 보이지 않게.""")
        String partnerName2,
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
        @Schema(description = "판매채널(레거시 schDataDT.type1)") String salesChannel,
        @Schema(description = "비고") String memo
) {
    /** 거래처 정보 없이(예전 호출부 호환). 거래처명1·2는 학교에 적힌 값만 쓴다. */
    public static SchoolSearchRow from(School s) {
        return from(s, null);
    }

    /**
     * @param partner 학교의 거래처코드로 찾은 거래처(없으면 null).
     *        거래처명1(상호)·거래처명2(풀네임)는 <b>거래처 마스터에만</b> 있다 —
     *        학교 마스터엔 거래처명이 한 칸뿐이라 둘로 나눌 수 없다.
     */
    public static SchoolSearchRow from(School s, com.daesung.sales.partner.entity.Partner partner) {
        String loc = blankToNull(s.getPartnerLoc());
        String name = blankToNull(s.getCustName());
        String locName = (loc == null) ? name : (name == null ? loc : loc + " " + name);
        String name1 = (partner == null) ? null : blankToNull(partner.getName1());
        String name2 = (partner == null) ? null : blankToNull(partner.getName());
        return new SchoolSearchRow(
                s.getSchoolCode(), s.getSchoolName(), s.getCityCode(),
                blankToNull(s.getRegion()), s.getCity(),
                name1, (name2 != null) ? name2 : name,
                loc, name, locName,
                s.getMockPartnerName(), s.getIcPartnerName(),
                s.getClientCategory(), s.getSalesChannel(), s.getMemo());
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
}
