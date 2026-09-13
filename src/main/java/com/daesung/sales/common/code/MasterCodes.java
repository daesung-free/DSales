package com.daesung.sales.common.code;

import java.util.List;

/**
 * 값이 정해진 마스터 항목들. 근거: 발주처 「데이터 구분값정리」(2026-08-05) + 레거시 실데이터.
 *
 * <p>★<b>enum이 아니라 문자열 상수인 이유</b> — 저장 컬럼이 VARCHAR이고 값 자체가 한글이다.
 * {@code @Enumerated(STRING)}으로 바꾸면 DB에 {@code G3} 같은 상수명이 들어가 기존 데이터와
 * 어긋나고, 마이그레이션 없이는 읽지도 못한다. 여기서는 <b>들어오는 값을 좁히는 것</b>이 목적이라
 * 저장 형태는 그대로 두고 정규화만 건다.
 */
public final class MasterCodes {

    private MasterCodes() {
    }

    // ── 거래처구분 ────────────────────────────────────────────────
    /**
     * 근거: 구분값정리 8탭 "특약점/기타학원/B2B/대성/자사몰".
     * 레거시 실데이터에서도 특약점 62 · 기타학원 8 · B2B 2 확인(대성·자사몰은 미사용이나 정본에 있어 포함).
     */
    // ★불변 List로 노출한다. public String[] 은 밖에서 내용을 바꿀 수 있어
    //   "정해진 값"이라는 약속이 코드 한 줄로 깨진다.
    public static final List<String> CLIENT_CATEGORY =
            List.of("특약점", "기타학원", "B2B", "대성", "자사몰");

    private static final String[][] CLIENT_CATEGORY_ALIAS = {
            {"B2B", "b2b"},
            // ★레거시 custData의 '기타' 39건은 전부 학원·교육업체다(A30D 대역 — 학원운영·보습학원·
            //   스터디카페…). 구분값이 정리되기 전의 옛 표기라, 초기 적재 때 그대로 받는다.
            //   받지 않으면 39건이 거래처구분 없이 들어가고 아무도 다시 채워 넣지 않는다.
            {"기타학원", "기타"},
    };

    public static String clientCategory(String raw) {
        return CodeValues.resolve("거래처구분", raw, CLIENT_CATEGORY, CLIENT_CATEGORY_ALIAS);
    }

    // ── 상품구분 — ⛔값을 못 박지 않는다(근거가 어긋난다) ───────────────
    //   정본 6탭:   "대분류(모의고사/교재) · 상품구분(**세트/단품**)"
    //   우리 모델:  세트 여부는 이미 boolean `set` 필드가 담당한다.
    //   우리 코드:  productType 주석은 "레거시 bookData.type **원시값**"이고,
    //              실제로 쓰이는 값은 "교재"·"모의고사"다 — 정본 기준이면 대분류 자리다.
    //   → 무엇을 담는 칸인지가 확정되지 않아 값을 좁히면 등록이 막힌다.
    //     발주처 확인 후 정하고, 그때까지는 자유 입력으로 둔다.

    // ── 학년 ──────────────────────────────────────────────────────
    /**
     * 저장값은 <b>숫자만</b>({@code 1·2·3}). 프론트 셀렉트가 그렇게 쓰고 있다
     * ({@code logisticsScreens.ts} — value `1`·`2`·`3`, label `N학년`).
     *
     * <p>‼️운영 데이터에 {@code 고3}이 9건 섞여 있었다. 별칭으로 받아 {@code 3}으로 모은다 —
     * 갈려 있으면 필터에서 서로 안 잡혀 리포트가 두 줄로 쪼개진다.
     */
    public static final List<String> GRADE = List.of("1", "2", "3");

    private static final String[][] GRADE_ALIAS = {
            {"1", "고1", "1학년", "고등학교1학년"},
            {"2", "고2", "2학년", "고등학교2학년"},
            {"3", "고3", "3학년", "고등학교3학년"},
    };

    public static String grade(String raw) {
        return CodeValues.resolve("학년", raw, GRADE, GRADE_ALIAS);
    }

    /** 화면 표기. 저장값은 숫자뿐이라 서버가 이름을 붙여 준다 — 화면이 매핑표를 들고 있지 않게. */
    public static String gradeName(String code) {
        return (code == null || code.isBlank()) ? null : code + "학년";
    }
}
