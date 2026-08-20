package com.daesung.sales.logistics.entity;

/**
 * 발송구분. 근거: 정본 26p [업데이트 2026-07-25 ★클라이언트 정정★] —
 * "작업 대기 리스트의 '상태변경' 옆에 '발송구분(택배/화물)' 필드 추가.
 * '택배' 선택 시 담당자 정보가 노출되어 물류가 거래명세서를 보고 택배/화물 여부 판단 후 발송".
 *
 * <p>값은 <b>둘뿐이다</b>. 발주처가 화물/택배 두 갈래로만 나눠 운영한다
 * (대량은 화물, 개인·소량은 택배).
 *
 * <p>⚠️기본값을 두지 않는다. 정본이 "발송구분을 물류가 매번 수동 판단하는지
 * 자동 기본값이 있는지"를 <b>미해결로 남겨</b>ㅤ두었다. 임의로 기본값을 정하면
 * 물류가 고르지 않은 건까지 한쪽으로 발송된 것처럼 보인다 — 미지정은 미지정으로 둔다.
 */
public enum DeliveryType {

    /** 택배 — 개인·소량. 이때 수령인(담당자) 정보가 화면에 노출된다. */
    COURIER("택배"),

    /** 화물 — 대량(대신화물 등). */
    FREIGHT("화물");

    private final String label;

    DeliveryType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

}
