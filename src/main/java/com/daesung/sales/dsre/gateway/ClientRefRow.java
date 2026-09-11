package com.daesung.sales.dsre.gateway;

/**
 * DSRE2 거래처 원본 한 줄. 출처: {@code tbl_cust_info} (+ 도시명은 {@code tbl_city_info}).
 *
 * <p>★<b>거래처 실데이터는 DSRE2에 있다.</b> 우리 마스터에 있는 {@code P-SEOUL} 같은 행은
 * 사업자번호가 {@code 000-01-0000n} 연번인 테스트 시드다 — 그걸 손으로 채워 봐야 가짜를 채우는 것이다.
 *
 * <p>‼️학교 동기화가 이미 {@code tbl_cust_info}를 조인한다. 거래처가 먼저 들어와 있지 않으면
 * 학교에 거래처명·도시가 <b>빈 채로</b> 들어온다({@code SchoolSyncResult.missingPartnerInfo}).
 * 순서는 거래처 → 학교다.
 *
 * @param code     거래처코드 CUST_CD
 * @param name1    상호만 CUST_NM
 * @param name     풀네임 CUST_FNM — 우리 {@code Partner.name}
 * @param cityName 도시명 CITY_CD → tbl_city_info.CITY_NM
 * @param region   관할명 CITY_NM(컬럼명과 뜻이 어긋난다 — 원본이 그렇다)
 * @param bizNo    사업자번호 REG_NO
 * @param bossName 대표자명 OWNER_NM
 * @param expired  만료지사 여부 END_GUBUN='Y'
 */
public record ClientRefRow(
        String code,
        String name1,
        String name,
        String cityName,
        String region,
        String bizNo,
        String bossName,
        String bizStatus,
        String bizItem,
        String tel1,
        String tel2,
        String cellPhone,
        String fax,
        String email1,
        String email2,
        String zip,
        String addr,
        Integer supplyRate,
        boolean expired
) {
}
