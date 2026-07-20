package com.daesung.sales.consignment.entity;

/** 위탁 미결 상태. OPEN=미정산, PARTIAL=부분정산, CLOSED=정산완료. 근거: 로직B. */
public enum ConsignmentStatus {
    OPEN,
    PARTIAL,
    CLOSED
}
