package com.daesung.sales.product.entity;

/**
 * 상품 대분류 — <b>5종 고정</b>. 근거: 발주처 회신 2026-08-20「상품 마스터 대분류/매출구분」.
 *
 * <p>집계의 기준 축이다. 그 아래 세부구분({@link SalesDivision})으로 드릴다운한다.
 *
 * <p>★ 레거시 교차검증 — 새로 만든 값이 아니라 원래 쓰던 축이다.
 * {@code CommonDB.vb:151~161}이 분류코드 첫 글자로 같은 구분을 만들고 있었다:
 * {@code 'H'→교재 / in('I','S')→기타고사 / 'M'→모의고사 / 'N'→특강}.
 * 레거시는 코드 글자에 숨겨 놨고 우리는 필드로 드러낼 뿐이다.
 *
 * <p>★ 이 목록은 사용자가 늘리지 못한다. 늘어나는 쪽은 세부구분이다 —
 * 회신이 "대분류 5종 고정 / 세부구분은 직접 추가·삭제"로 갈라 놓았다.
 */
public enum MajorCategory {

    MOCK_EXAM("모의고사"),
    TEXTBOOK("교재"),
    ETC_EXAM("기타고사"),
    SPECIAL_LECTURE("특강"),
    ETC("기타"),

    /**
     * IC — <b>화면에서 숨긴다</b>. 회신: "현재 미사용이나 데이터는 보존해야 하니 화면상 숨김 처리".
     *
     * <p>지우지 않는 이유가 곧 보존이다. 값을 없애면 과거 매출이 가리키던 구분이 사라진다.
     * 레거시에서도 IC는 기타고사에 함께 묶여 있었다.
     */
    IC("IC", false);

    private final String label;
    private final boolean visible;

    /**
     * 코드명·한글 <b>둘 다</b> 받는다. 근거: 프론트 회신(2026-09-17) B-12 —
     * 세부구분 등록이 "요청 본문을 해석할 수 없습니다"(400)로 막혔다.
     *
     * <p>★화면은 목록에서 고른 <b>한글</b>을 그대로 되보낸다. 응답이 한글을 주는데
     * 요청만 enum 이름을 요구하면 화면이 변환표를 따로 들어야 하고, 그 표가 언젠가 갈린다.
     * 거래분류(`SALES` vs `매출`)·권한키(`PERIOD_LOCK` vs `periodLock`)에서 같은 일을 겪었다.
     *
     * <p>‼️모르는 값은 그대로 400이다 — 아무 문자열이나 받으면 오타가 조용히 통과한다.
     */
    @com.fasterxml.jackson.annotation.JsonCreator
    public static MajorCategory from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        for (MajorCategory c : values()) {
            if (c.name().equals(v) || c.label.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException("알 수 없는 대분류입니다: " + raw
                + " (모의고사/교재/기타고사/특강/기타)");
    }

    MajorCategory(String label) {
        this(label, true);
    }

    MajorCategory(String label, boolean visible) {
        this.label = label;
        this.visible = visible;
    }

    /** 화면 표기명(모의고사·교재 …). 응답에 코드와 함께 실어 프론트가 매핑표를 들고 있지 않게 한다. */
    public String label() {
        return label;
    }

    /** 상품 등록 등 선택 목록에 노출할지. IC만 false. */
    public boolean visible() {
        return visible;
    }
}
