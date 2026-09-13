package com.daesung.sales.dsre.gateway;

/**
 * DSRE2 지사↔학교/학원 매핑 1행(학교관리 가져오기 원본).
 * 출처: {@code tbl_cust_ref} × {@code tbl_cust_info} × {@code tbl_school_info}/{@code tbl_hakwon_info}.
 *
 * @param custCode   거래처코드(CUST_CD) — 매칭키
 * @param schoolCode 학교/학원코드(MGR_CD) — 매칭키
 * @param school     학교 여부(MGR_GN: 'S'=학교, 'A'=학원)
 * @param custName   거래처명(CUST_FNM 지사 풀네임)
 * @param city       도시명(거래처 CITY_CD → tbl_city_info.CITY_NM)
 * @param region     지역·관할(거래처 CITY_NM 관활명)
 * @param schoolName 학교/학원명(SCH_NM 또는 HAK_NM)
 * @param machulCode 담당 특약점의 <b>매출코드</b>(MACHUL_CD) — 레거시 {@code schData.mCustCode/iCustCode}
 * @param partnerLabel 담당 특약점 표시명(레거시와 같게 {@code CITY_NM + ' ' + CUST_NM})
 */
public record SchoolRefRow(
        String custCode,
        String schoolCode,
        boolean school,
        String custName,
        String city,
        String region,
        String schoolName,
        String machulCode,
        String partnerLabel
) {
}
