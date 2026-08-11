package com.daesung.sales.dsre;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.dsre.gateway.OrderState;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 진행상태 코드↔한글명 회귀 고정.
 *
 * <p>이 매핑은 우리가 정한 것이 아니라 DSRE2 {@code tbl_request_info.STATE} DDL 주석에 있던 것이고,
 * 발주처 회신(자료요청서 3-2(가))과 글자까지 일치한다. 한 글자라도 바꾸면 특약점 사이트·본사 화면과
 * 표기가 어긋나므로 값 자체를 테스트로 박아둔다.
 */
@DisplayName("주문 진행상태 매핑")
class OrderStateTest {

    @Test
    @DisplayName("DSRE2 코드값과 한글명이 DDL 주석 정의 그대로다")
    void 코드_한글_매핑() {
        assertThat(List.of(OrderState.values()).stream().map(s -> s.code() + "=" + s.label()).toList())
                .containsExactly(
                        "A=접수완료", "G=상품검수", "S=상품준비중",
                        "W=발송준비중", "D=발송완료", "C=삭제");
    }

    @Test
    @DisplayName("코드 조회 — 뒤 공백이 붙어도 매칭된다(char 고정폭 컬럼)")
    void 코드_조회() {
        assertThat(OrderState.ofCode("S")).contains(OrderState.PREPARING);
        assertThat(OrderState.ofCode(" D ")).contains(OrderState.SHIPPED);
        assertThat(OrderState.ofCode(null)).isEmpty();
        assertThat(OrderState.ofCode("Z")).isEmpty();
    }

    @Test
    @DisplayName("모르는 코드는 감추지 않고 코드값 그대로 보여준다")
    void 미정의_코드() {
        // 운영 DSRE2에 우리가 모르는 값이 생겨도 조회는 살아 있어야 하고,
        // 무엇이 왔는지가 화면에서 보여야 한다(빈칸으로 삼키면 원인 추적이 안 된다).
        assertThat(OrderState.labelOf("Z")).isEqualTo("Z");
        assertThat(OrderState.labelOf("D")).isEqualTo("발송완료");
        assertThat(OrderState.labelOf(null)).isEmpty();
    }
}
