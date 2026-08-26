package com.daesung.sales.sale.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.common.money.Amounts;
import com.daesung.sales.dsre.gateway.DuffChargeMode;
import com.daesung.sales.dsre.gateway.DuffSalesRow;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 매출일괄등록(14p, 더프)의 <b>청구 판정</b> 회귀 고정.
 * 근거: 레거시 {@code 매출가져오기.vb:425} CASE + 정본 14p
 * "처리구분·청구구분에 따라 단가·청구인원 결정, 우선순위: 등록→처리비처리→신청(기본값)".
 *
 * <p>DSRE2 연결이 필요 없는 순수 판정이라 통합테스트가 아니라 여기서 본다 —
 * 이 규칙이 틀리면 <b>청구 금액이 통째로 틀린다</b>. DSRE가 꺼진 CI에서도 반드시 돌아야 한다.
 */
@DisplayName("매출일괄등록(더프) 청구 판정")
class DuffChargeRuleTest {

    /** 인원 4종만 다른 행을 만든다(신청 100 / 처리 70 / 비처리 30 / 등록 50). */
    private static DuffSalesRow row(String procType, String chargeType) {
        return new DuffSalesRow(1, LocalDate.of(2026, 7, 1), "M001", "C001", "거래처", "거래처(주)",
                "서울", "S001", "학교", "3", "A0150", "더 프리미엄", "3752", "7월 16일",
                5, procType, chargeType, 100, 70, 30, 50, 10000, 70, 0, null);
    }

    @Nested
    @DisplayName("청구인원 우선순위")
    class 우선순위 {

        @Test
        @DisplayName("청구구분 R이면 무조건 등록인원 — 모드보다 먼저다")
        void 등록이_최우선() {
            for (DuffChargeMode m : DuffChargeMode.values()) {
                var c = DuffSalesImportService.charge(row("1", "R"), m);
                assertThat(c.inwon()).as("모드 %s", m).isEqualTo(50);
                assertThat(c.basis()).isEqualTo("REGISTERED");
            }
        }

        @Test
        @DisplayName("모드가 처리/비처리로 좁혀져 있으면 그 인원을 쓴다")
        void 모드가_그다음() {
            assertThat(DuffSalesImportService.charge(row("1", "S"), DuffChargeMode.PROCESSED).inwon())
                    .isEqualTo(70);
            assertThat(DuffSalesImportService.charge(row("1", "S"), DuffChargeMode.UNPROCESSED).inwon())
                    .isEqualTo(30);
        }

        @Test
        @DisplayName("★처리구분 N이면 비처리인원이다 — 화면 안내문('N=신청인원')이 아니라 코드를 따랐다")
        void 처리구분_N은_비처리() {
            var c = DuffSalesImportService.charge(row("N", "S"), DuffChargeMode.ALL);
            // 레거시 안내문(매출가져오기.Designer.vb:470)은 "N - 신청인원"이라 적혀 있지만
            // 실제 CASE는 비처리인원을 쓴다. 실제로 청구된 금액이 그쪽이다.
            assertThat(c.inwon()).as("신청 100이 아니라 비처리 30").isEqualTo(30);
            assertThat(c.basis()).isEqualTo("UNPROCESSED");
        }

        @Test
        @DisplayName("그 외(처리구분이 회차 숫자)는 처리인원")
        void 기본은_처리() {
            // proc_yn2 실값은 Y/N이 아니라 '1'·'5'·'N'이다(복제본 실측). 숫자는 회차다.
            for (String round : new String[] {"1", "5"}) {
                var c = DuffSalesImportService.charge(row(round, "S"), DuffChargeMode.ALL);
                assertThat(c.inwon()).as("처리구분 %s", round).isEqualTo(70);
                assertThat(c.basis()).isEqualTo("PROCESSED");
            }
        }
    }

    @Nested
    @DisplayName("모드별로 보이는 행")
    class 행_필터 {

        @Test
        @DisplayName("모두 — 다 보인다")
        void 모두() {
            assertThat(DuffChargeMode.ALL.accepts("N", null)).isTrue();
            assertThat(DuffChargeMode.ALL.accepts("1", "S")).isTrue();
        }

        @Test
        @DisplayName("처리 — 처리구분 N은 뺀다")
        void 처리() {
            assertThat(DuffChargeMode.PROCESSED.accepts("1", "S")).isTrue();
            assertThat(DuffChargeMode.PROCESSED.accepts("N", "S")).isFalse();
        }

        @Test
        @DisplayName("★비처리 — N뿐 아니라 '청구구분 T'(처리+비처리)도 대상이다")
        void 비처리() {
            assertThat(DuffChargeMode.UNPROCESSED.accepts("N", "S")).isTrue();
            // T는 처리분과 비처리분을 함께 청구하는 계약이라 비처리로 볼 때도 남아야 한다.
            assertThat(DuffChargeMode.UNPROCESSED.accepts("1", "T")).isTrue();
            assertThat(DuffChargeMode.UNPROCESSED.accepts("1", "S")).isFalse();
        }
    }

    @Test
    @DisplayName("★총금액은 인원을 넣어 한 번에 계산한다 — 단가를 먼저 버리면 매출이 사라진다")
    void 버림은_한번만() {
        // 복제본 실데이터에서 실제로 나온 값: 정가 1 · 공급률 70 · 인원 110.
        // 단가를 먼저 버리면 floor(0.7)=0 → 총금액 0원. 매출이 통째로 증발한다.
        long unitFirst = Amounts.supplyOf(1, 70, 1) * 110;
        long atOnce = Amounts.supplyOf(1, 70, 110);

        assertThat(unitFirst).as("단가 선버림 — 이렇게 하면 안 된다").isZero();
        assertThat(atOnce).as("한 번에 계산 = 실제 청구액").isEqualTo(77);
    }
}
