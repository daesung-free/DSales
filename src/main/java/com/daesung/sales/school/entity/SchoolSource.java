package com.daesung.sales.school.entity;

/**
 * 학교/학원 행의 출처(35p 동기화).
 *
 * <p>보존형 동기화의 핵심 구분자다. DSRE2에 없는 매출프로그램 전용 데이터
 * (문서 예시: 거래처코드 20005 / 온라인스터디카페)를 동기화가 미사용 처리하거나 지우면 안 되므로,
 * "DSRE에서 사라졌다"는 판정을 {@link #DSRE} 행에만 적용한다.
 */
public enum SchoolSource {
    /** DSRE2 tbl_cust_ref에서 동기화된 행. 동기화 대상. */
    DSRE,
    /** 매출프로그램에서 직접 등록한 전용 행. 동기화가 건드리지 않음. */
    MANUAL
}
