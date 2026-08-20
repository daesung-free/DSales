package com.daesung.sales.sale.dto;

/**
 * 응시현황(기간별) 원자료 — 지역·거래처·학교·학년·연월·처리구분 단위 인원(수량).
 *
 * <p>크로스탭 조립(월 컬럼·소계·총계)은 서비스에서 한다. 쿼리는 flat만 낸다 —
 * 레거시가 월·학년·영역을 SQL에 하드코딩해서 해마다 손을 대야 했고, 결국 2022년에 멈췄다.
 */
public interface AttendancePeriodAgg {

    String getRegion();

    String getPartnerCode();

    String getPartnerName();

    String getSchoolCode();

    String getSchoolName();

    String getGrade();

    /** 연(YYYY) — 기간이 해를 넘길 수 있어 월만으로는 칸을 못 정한다. */
    int getYear();

    int getMonth();

    /** 성적처리 인원. */
    Long getGradedQty();

    /** 비처리 인원(미지정 포함). */
    Long getUngradedQty();
}
