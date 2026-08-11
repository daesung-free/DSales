package com.daesung.sales.consignment.entity;

/**
 * 위탁 정산상태. 근거: 로직B + 발주처 확정(자료요청서 3-2(다)) — 제안 ①번 채택.
 *
 * <p>위탁출고는 내보낸 시점에 매출·무상·반품 어디에도 해당하지 않는다(정산이 끝나야 매출이 된다).
 * 그 '아직 정산 안 된 상태'를 표현하려고 구분(상세)을 4값으로 늘리는 대신
 * <b>위탁 건에만 붙는 별도 축</b>을 두기로 확정됐다 — 그게 이 enum이고, 기존 3값(매출/무상/반품)을
 * 쓰는 다른 화면·집계는 손대지 않는다.
 *
 * <p>{@link #label()}은 발주처가 회신에 쓴 표기(미정산/부분정산/정산완료) 그대로다.
 */
public enum ConsignmentStatus {

    /** 미정산 — 한 번도 정산되지 않음 */
    OPEN("미정산"),
    /** 부분정산 — 일부만 정산됨(분할정산 진행 중) */
    PARTIAL("부분정산"),
    /** 정산완료 — 미결 잔여 0 */
    CLOSED("정산완료");

    private final String label;

    ConsignmentStatus(String label) {
        this.label = label;
    }

    /** 화면 표기용 한글명. 발주처 회신 표기와 일치시킨다. */
    public String label() {
        return label;
    }
}
