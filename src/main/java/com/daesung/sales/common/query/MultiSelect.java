package com.daesung.sales.common.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 조회 필터의 <b>다중선택</b> 정규화. 근거: 발주처 화면검토(2026-08-31) —
 * 화면3 "무상/유상/반품/입고 <b>중복선택</b> 체크박스", 공통 "트리뷰 + 체크박스 다중선택".
 *
 * <p>★단수 파라미터는 <b>버리지 않고 합친다</b>. 프론트가 이미 붙어 있는 단수 파라미터
 * ({@code partnerId} 등)를 어느 날 서버가 무시하기 시작하면, 화면은 조용히 전체를 보여준다.
 * 필터가 안 먹는 것은 에러도 안 나고 결과가 그럴듯해서 오래 안 들킨다.
 * 그래서 단수는 "원소가 하나인 다중선택"으로 취급한다.
 *
 * <p>★반환 {@code null}은 <b>조건 미적용(전체)</b>이다. 빈 리스트와 구별해야 한다 —
 * 빈 리스트를 "전체"로 읽으면, 체크박스를 전부 푼 화면이 전체를 보여주게 된다.
 * 다만 HTTP 파라미터로는 "안 보냄"과 "빈 배열"을 구별하기 어려워, 빈 입력도 미적용으로 본다.
 */
public final class MultiSelect {

    private MultiSelect() {
    }

    /**
     * 단수 하나 + 복수 목록을 합집합으로 정규화한다. 입력 순서를 유지하고 중복·null은 버린다.
     *
     * @return 조건에 쓸 값들. <b>미적용이면 null</b>(빈 리스트가 아니다)
     */
    public static <T> List<T> merge(T single, Collection<T> multi) {
        Set<T> merged = new LinkedHashSet<>();
        if (multi != null) {
            for (T v : multi) {
                if (v != null) {
                    merged.add(v);
                }
            }
        }
        if (single != null) {
            merged.add(single);
        }
        return merged.isEmpty() ? null : new ArrayList<>(merged);
    }

    /** 조건 미적용 여부. {@link #merge}의 결과를 그대로 받는다. */
    public static boolean isAny(Collection<?> values) {
        return values == null || values.isEmpty();
    }

    /**
     * JPQL {@code in :param} 에 넘길 안전한 컬렉션.
     *
     * <p>{@code in ()} 은 빈 컬렉션이면 문법 오류가 나거나 DB마다 다르게 동작한다.
     * 조건을 미적용할 때는 짝이 되는 {@code :anyXxx = true} 플래그가 {@code in} 절 자체를
     * 건너뛰므로, 여기서는 <b>파싱만 통과할 자리채움</b>을 준다. 값은 쓰이지 않는다.
     */
    public static <T> Collection<T> orPlaceholder(Collection<T> values, T placeholder) {
        return isAny(values) ? List.of(placeholder) : values;
    }
}
