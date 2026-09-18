package com.daesung.sales.salestype.entity;

/**
 * 출고유형(6종). 근거: 시트2① 분류축 매핑표 / API 스펙 ShipmentType.
 *
 * <p>★<b>한글 표기를 여기서 준다.</b> 화면마다 라벨표를 만들면 언젠가 갈린다 —
 * 실제로 엑셀에 {@code NORMAL_SHIP}이 그대로 찍히고 있었다(2026-09-17 프론트 지적).
 * 거래분류({@code TradeClass})는 이미 이 방식이라 두 축의 규칙을 맞춘다.
 */
public enum ShipmentType {

    /** 정상출고 → SALE, 즉시 매출. */
    NORMAL_SHIP("정상출고"),
    /** 위탁출고 → SALE, 매출 미결 + 위탁창고 이동. */
    CONSIGN_SHIP("위탁출고"),
    /** 증정용 → FREE. */
    GIFT("증정용"),
    /** 교사용 → FREE. */
    TEACHER_USE("교사용"),
    /** 반품 → RETURN. */
    RETURN("반품"),
    /** 취소 → RETURN(발주처 2026-09-01 확정: 취소는 반품으로 고정). */
    CANCEL("취소");

    private final String label;

    ShipmentType(String label) {
        this.label = label;
    }

    /** 화면·엑셀에 찍히는 한글 표기. */
    public String label() {
        return label;
    }

    /** null 안전 변환. 값이 없으면 빈 문자열 — 엑셀 칸이 "null"로 찍히면 더 나쁘다. */
    public static String labelOf(ShipmentType type) {
        return (type == null) ? "" : type.label();
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
    public static ShipmentType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        for (ShipmentType c : values()) {
            if (c.name().equals(v) || c.label.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException("알 수 없는 값입니다: " + raw + " (정상출고/위탁출고/증정용/교사용/반품/취소)");
    }
}
