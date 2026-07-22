package com.daesung.sales.auth.entity;

/**
 * 직원 역할(RBAC). 기본안 — 정확한 권한 매트릭스는 발주처 확정 대기.
 * ADMIN=관리자(전체), FINANCE=재무(마감/수금/세무), LOGISTICS=물류(재고/실사),
 * SALES=영업(매출/주문), VIEWER=조회 전용.
 */
public enum Role {
    ADMIN,
    FINANCE,
    LOGISTICS,
    SALES,
    VIEWER
}
