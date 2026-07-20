package com.daesung.sales.salestype.entity;

/** 출고유형(6종). 근거: 시트2① 분류축 매핑표. */
public enum OutType {
    NORMAL,   // 정상출고
    CONSIGN,  // 위탁출고
    GIFT,     // 증정용
    TEACHER,  // 교사용
    RETURN,   // 반품
    CANCEL    // 취소
}
