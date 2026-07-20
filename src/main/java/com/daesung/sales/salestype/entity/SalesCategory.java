package com.daesung.sales.salestype.entity;

/** 회계구분(3종). 근거: 시트2① (출고유형→자동매핑) / API 스펙 SalesCategory. */
public enum SalesCategory {
    SALE,    // 매출
    FREE,    // 무상
    RETURN   // 반품
}
