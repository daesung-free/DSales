package com.daesung.sales.inventory.entity;

/** 재고 이벤트 유형(7종). 근거: 시트2④ 수불부 방정식 / 로직A. */
public enum TxnType {
    INBOUND,          // 입고
    OUTBOUND,         // 출고
    TRANSFER,         // 이고(창고 이동)
    BOM_ASSEMBLE,     // 세트 조립
    BOM_DISASSEMBLE,  // 세트 해체
    DISPOSE,          // 폐기
    RETURN            // 반품
}
