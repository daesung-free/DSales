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
    public static ContentType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        for (ContentType c : values()) {
            if (c.name().equals(v) || c.label.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException("알 수 없는 값입니다: " + raw + " (자체교재/매입 교재)");
    }
}
