package com.daesung.sales.receivable.entity;

/** 수금 유형. 근거: 레거시 수금등록.vb 라디오 4종. */
public enum CollectionType {
    CASH,        // 현금
    PROMISSORY,  // 어음
    PREPAY,      // 선수금
    REPLACE      // 대체
}
