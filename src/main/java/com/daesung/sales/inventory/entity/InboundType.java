package com.daesung.sales.inventory.entity;

/**
 * 입고구분. 근거: 요구사항정의서 8p 입고/대체등록 [정정 2026-07-28 재무팀].
 * 순매출조회(16p)의 외부콘텐츠 매입액은 PURCHASE(매입입고)로 등록된 입고만 집계한다
 * (NORMAL=인쇄소 등 자체 제작 입고는 매입원가에서 제외).
 */
public enum InboundType {
    /** 정상입고(인쇄소 등 자체 제작·조달). 매입원가에 포함되지 않음. */
    NORMAL,
    /** 매입입고(외부콘텐츠 자회사 직거래 매입). 16p 순매출조회 매입 데이터로 자동 연결. */
    PURCHASE
}
