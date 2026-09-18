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


    /**
     * 코드명·한글 <b>둘 다</b> 받는다.
     *
     * <p>★화면은 목록에서 고른 <b>한글</b>을 그대로 되보낸다. 응답이 한글을 주는데 요청만
     * enum 이름을 요구하면 화면이 변환표를 따로 들어야 하고, 그 표가 언젠가 갈린다.
     * 이 프로젝트에서 이미 여러 번 같은 400을 냈다(대분류·거래분류·권한키).
     *
     * <p>‼️모르는 값은 그대로 400이다 — 아무 문자열이나 받으면 오타가 조용히 통과한다.
     */
    @com.fasterxml.jackson.annotation.JsonCreator
    public static DeliveryType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        for (DeliveryType c : values()) {
            if (c.name().equals(v) || c.label.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException("알 수 없는 값입니다: " + raw + " (택배/화물)");
    }
}
