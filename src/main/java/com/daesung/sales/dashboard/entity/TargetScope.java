package com.daesung.sales.dashboard.entity;

/**
 * 매출목표의 대상 축. 근거: 자료요청서 1-6 회신 데이터.
 *
 * <p>기존엔 전사(product_id=null)와 상품 둘뿐이었는데, 발주처가 준 목표는 그 사이의
 * <b>사업부문</b> 단위였다(더프리미엄·D모의고사·학원 컨텐츠·외부 교재·논술·강모·평가고사).
 */
public enum TargetScope {
    /** 전사 총매출 */
    COMPANY,
    /** 사업부문 — scopeKey에 부문명 */
    DIVISION,
    /** 상품 단위 — productId */
    PRODUCT
}
