package com.daesung.sales.audit.entity;

/**
 * 기초정보 변경이력의 대상. 발주처가 지목한 세 가지(거래처·상품·단가)와 정확히 대응한다
 * (자료요청서 3-1 라 회신).
 */
public enum MasterEntityType {
    /** 거래처 */
    PARTNER("거래처"),
    /** 도서(상품) */
    PRODUCT("도서"),
    /** 거래처별 단가·노출 매핑 */
    PARTNER_PRICE("거래처별단가");

    private final String label;

    MasterEntityType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
