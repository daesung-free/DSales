package com.daesung.sales.product.entity;

/**
 * 콘텐츠 구분(신규 최상위 축). 근거: 정본 16p 순매출조회.
 *
 * <p>★<b>표기는 "매입 교재"다.</b> 발주처 화면검토 확인요청서(2026-08-31) —
 * "'외부 콘텐츠' 명칭은 '<b>매입 교재</b>'로 수정 부탁드립니다."
 * 코드값({@code EXTERNAL})은 그대로 둔다. 저장된 데이터와 API 계약을 바꾸면
 * 이름 하나 때문에 마이그레이션이 생기고 프론트도 다시 맞춰야 한다 —
 * 화면에 보이는 <b>라벨만</b> 바꾸면 되는 일이다.
 */
public enum ContentType {

    /** 자체교재 — 매입이 발생하지 않는다. 순매출 = 매출 − 반품. */
    SELF("자체교재"),

    /** 매입 교재(구 '외부콘텐츠') — 자회사 직거래 매입이 발생한다. 이익 = 매출 − 매입. */
    EXTERNAL("매입 교재");

    private final String label;

    ContentType(String label) {
        this.label = label;
    }

    /** 화면 표기. 서버가 내려줘야 화면마다 매핑표를 들고 있지 않는다. */
    public String label() {
        return label;
    }
}
