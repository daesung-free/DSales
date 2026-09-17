package com.daesung.sales.common.query;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * 조회화면 공통 키워드 검색.
 *
 * <p>정본 공통 요구 — "전 화면 Ctrl+F 검색 기본 제공". 그런데 리포트 11곳이 {@code keyword}를
 * <b>받지도 않았다.</b> 더 나쁜 건 400도 아니고 <b>조용히 무시</b>했다는 점이다 —
 * 화면은 검색했다고 믿는데 전체가 나오니, 담당자는 "검색이 안 먹네"가 아니라
 * <b>"이 거래처엔 이만큼 있구나"</b>로 잘못 읽는다(2026-09-16 프론트 번들·라이브 실측).
 *
 * <p>★<b>메모리에서 거른다.</b> 집계 쿼리의 WHERE에 넣으면 이월·합계가 걸러진 행만으로
 * 다시 계산돼 숫자가 바뀐다. 이 리포트들은 화면 한 장 분량이라 메모리 필터로 충분하다.
 * (제품수불부가 같은 이유로 이미 이 방식이다.)
 *
 * <p>‼️<b>합계행은 거르지 않는다.</b> 합계를 같이 걸러 내면 "검색 결과 3건인데 합계는 0"이
 * 되어 화면이 깨진 것처럼 보인다. 합계를 걸러진 것 기준으로 다시 계산할지는 화면마다
 * 다른 판단이라, 여기서는 <b>행만</b> 줄이고 합계는 호출부가 정한다.
 */
public final class Keywords {

    private Keywords() {
    }

    /** 검색어 정규화. 비었으면 null(= 필터 없음). */
    public static String norm(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 행 목록을 키워드로 거른다.
     *
     * @param rows   원본 행
     * @param keyword 검색어(null·공백이면 원본 그대로)
     * @param fields 행 하나에서 <b>검색 대상 문자열들</b>을 뽑는 함수. null 값은 건너뛴다.
     */
    public static <T> List<T> filter(List<T> rows, String keyword, Function<T, Object[]> fields) {
        String kw = norm(keyword);
        if (kw == null || rows == null) {
            return rows;
        }
        return rows.stream().filter(r -> matches(fields.apply(r), kw)).toList();
    }

    private static boolean matches(Object[] values, String kw) {
        if (values == null) {
            return false;
        }
        for (Object v : values) {
            if (v != null && v.toString().toLowerCase(Locale.ROOT).contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
