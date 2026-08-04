package com.daesung.sales.audit.entity;

/**
 * 상태변경 이력의 대상 종류. 감사에서 실제로 추궁받는 축만 둔다.
 *
 * <p>논리삭제(deleted_at/deleted_by)는 여기 넣지 않는다 — 행 자체에 누가·언제가 이미 남아 중복이다.
 */
public enum StatusEntityType {
    /** 매출 — 취소 여부 */
    SALE,
    /** 월마감 — 잠금/해제. 같은 월을 여러 번 여닫으면 기존 구조로는 중간 기록이 사라진다 */
    PERIOD_LOCK,
    /** 위탁 미결 — 미정산/부분정산/완료 */
    CONSIGNMENT_OUT,
    /** 학교 — 사용/미사용(DSRE 동기화 배치가 바꾼다) */
    SCHOOL,
    /** 계정 — 활성/비활성 */
    APP_USER
}
