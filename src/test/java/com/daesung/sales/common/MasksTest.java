package com.daesung.sales.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.common.mask.Masks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 사업자주민번호 마스킹 규칙(DB-73) 회귀 고정. */
@DisplayName("민감정보 마스킹")
class MasksTest {

    @Test
    @DisplayName("주민번호는 앞 7자리만 남는다 — 구분자 유무와 무관")
    void 주민번호_마스킹() {
        assertThat(Masks.residentNo("800101-1234567")).isEqualTo("800101-1******");
        assertThat(Masks.residentNo("8001011234567")).isEqualTo("800101-1******");
        assertThat(Masks.residentNo(null)).isNull();
        assertThat(Masks.residentNo("")).isEmpty();
    }

    @Test
    @DisplayName("형식이 다른 값도 원본을 그대로 흘리지 않는다")
    void 비정형_값() {
        // 자릿수가 모자라면 형식을 단정할 수 없다. 그래도 절반은 가린다 —
        // "형식이 이상하니 그냥 통과"가 제일 위험하다.
        assertThat(Masks.residentNo("12345")).isNotEqualTo("12345").contains("*");
    }

    @Test
    @DisplayName("마스킹된 값을 되받으면 저장하지 않도록 판별한다")
    void 마스킹_판별() {
        // 화면이 마스킹 값을 그대로 담아 저장하면 원본이 파괴된다. 그걸 막는 판별.
        assertThat(Masks.isMasked("800101-1******")).isTrue();
        assertThat(Masks.isMasked("800101-1234567")).isFalse();
        assertThat(Masks.isMasked(null)).isFalse();
    }
}
