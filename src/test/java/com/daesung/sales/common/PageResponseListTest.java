package com.daesung.sales.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.common.response.PageResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 메모리 목록을 페이지로 감싸는 {@link PageResponse#ofList} 회귀 고정.
 *
 * <p>DSRE2 주문조회처럼 <b>원본이 페이징을 지원하지 않는</b> 조회에 쓴다.
 * ★총건수는 자른 뒤가 아니라 <b>전체</b>여야 한다 — 화면 하단 건수가 페이지마다 달라지면
 * 담당자는 데이터가 빠진 줄 안다.
 */
@DisplayName("목록 → 페이지 변환")
class PageResponseListTest {

    private static final List<Integer> ALL = List.of(1, 2, 3, 4, 5, 6, 7);

    @Test
    @DisplayName("★총건수는 잘라도 전체를 유지한다")
    void 총건수는_전체() {
        PageResponse<Integer> p = PageResponse.ofList(ALL, 0, 3);

        assertThat(p.getContent()).containsExactly(1, 2, 3);
        assertThat(p.getTotalElements()).as("자른 3이 아니라 전체 7").isEqualTo(7);
        assertThat(p.getTotalPages()).isEqualTo(3);
        assertThat(p.isLast()).isFalse();
    }

    @Test
    @DisplayName("마지막 페이지는 남은 만큼만 · last=true")
    void 마지막_페이지() {
        PageResponse<Integer> p = PageResponse.ofList(ALL, 2, 3);

        assertThat(p.getContent()).containsExactly(7);
        assertThat(p.isLast()).isTrue();
    }

    @Test
    @DisplayName("범위를 넘는 페이지는 빈 목록 — 예외가 아니다")
    void 범위_밖() {
        PageResponse<Integer> p = PageResponse.ofList(ALL, 99, 3);

        assertThat(p.getContent()).isEmpty();
        assertThat(p.getTotalElements()).as("건수는 그대로 알려준다").isEqualTo(7);
        assertThat(p.isLast()).isTrue();
    }

    @Test
    @DisplayName("빈 목록·이상한 값도 죽지 않는다")
    void 경계값() {
        assertThat(PageResponse.ofList(List.<Integer>of(), 0, 10).getContent()).isEmpty();
        assertThat(PageResponse.ofList(ALL, -1, 0).getContent())
                .as("음수 페이지·0 크기는 기본값으로 보정").hasSize(7);
    }
}
