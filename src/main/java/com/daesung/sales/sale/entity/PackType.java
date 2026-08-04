package com.daesung.sales.sale.entity;

/**
 * 포장구분. 근거: 레거시 distData.packType(개별1/개별2/반별).
 *
 * <p>같은 축이 세 시스템에 다른 이름으로 있다 — 하나로 통일한다.
 * <ul>
 *   <li>레거시 distData : 개별1 / 개별2 / 반별
 *   <li>정본 36p 물류비용등록 작업구분 : 개별봉투 / 개별봉투(SET) / 반별봉투
 *   <li>DSRE2 tbl_logis_cost.PACKTYPE : 1 / 3(SET) / 2
 * </ul>
 * IC회차별작업현황(구 물류)이 이 구분으로 수량을 나눠 보여준다.
 */
public enum PackType {
    /** 개별1 = 개별봉투 (DSRE PACKTYPE 1) */
    INDIVIDUAL_1,
    /** 개별2 = 개별봉투(SET) (DSRE PACKTYPE 3) */
    INDIVIDUAL_2,
    /** 반별 = 반별봉투 (DSRE PACKTYPE 2) */
    CLASS_BUNDLE
}
