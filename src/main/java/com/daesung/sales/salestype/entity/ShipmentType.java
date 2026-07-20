package com.daesung.sales.salestype.entity;

/** 출고유형(6종). 근거: 시트2① 분류축 매핑표 / API 스펙 ShipmentType. */
public enum ShipmentType {
    NORMAL_SHIP,   // 정상출고 → SALE, 즉시 매출
    CONSIGN_SHIP,  // 위탁출고 → SALE, 매출 미결 + 위탁창고 이동
    GIFT,          // 증정용 → FREE
    TEACHER_USE,   // 교사용 → FREE
    RETURN,        // 반품 → RETURN
    CANCEL         // 취소 → RETURN
}
