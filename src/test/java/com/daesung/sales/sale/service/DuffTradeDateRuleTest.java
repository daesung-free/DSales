package com.daesung.sales.sale.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 매출일괄등록(더프)의 <b>거래일자 판정</b> — 마감 검사의 기준이 되는 값이다.
 *
 * <p>근거: 프론트 실측 2026-10-23 §4① —
 * "마감 검사 기준이 지금은 미리보기 끝 날짜(toDate)로만 보는 것 같습니다.
 * 실제 거래일로 검사해 주실 수 있을까요? 지금은 거래일이 마감된 달이어도 끝 날짜만
 * 열려 있으면 들어갑니다. 반대로 지난달이 마감됐으면 거래일을 오늘로 지정해도 막힙니다."
 *
 * <p>‼️<b>왜 통합테스트가 아닌가</b>: 매출일괄등록은
 * {@code @ConditionalOnProperty(daesung.dsre.enabled=true)} 뒤에 있고 CI는 DSRE를 끄고 돈다 —
 * 빈 자체가 만들어지지 않아 HTTP로 짚을 수 없다. 규칙만이라도 여기서 못박는다
 * ({@code DuffChargeRuleTest}·{@code OrderStateChangeTest}와 같은 이유다).
 */
@DisplayName("매출일괄등록(더프) 거래일자 판정")
class DuffTradeDateRuleTest {

    private static final LocalDate TO = LocalDate.of(2026, 10, 31);
    private static final LocalDate REQ = LocalDate.of(2026, 8, 14);
    private static final LocalDate PICKED = LocalDate.of(2026, 10, 23);

    @Test
    @DisplayName("거래일자를 지정하면 그 날로 통일된다 — 신청일이 있어도 이긴다")
    void 지정하면_그날() {
        assertThat(DuffSalesImportService.tradeDateOf(PICKED, REQ, TO)).isEqualTo(PICKED);
    }

    @Test
    @DisplayName("★비우면 줄마다 신청일 — 한 호출 안에서도 달이 섞인다")
    void 비우면_신청일() {
        assertThat(DuffSalesImportService.tradeDateOf(null, REQ, TO)).isEqualTo(REQ);

        // ‼️이것이 결함의 핵심이었다. 조회 끝 날짜는 10월인데 이 줄은 **8월** 매출이다.
        //   마감 검사를 끝 날짜로 하면 8월이 마감돼 있어도 통과한다.
        assertThat(DuffSalesImportService.tradeDateOf(null, REQ, TO).getMonthValue())
                .as("조회 끝 날짜(10월)가 아니라 신청일의 달(8월)").isEqualTo(8);
        assertThat(DuffSalesImportService.tradeDateOf(null, REQ, TO)).isNotEqualTo(TO);
    }

    @Test
    @DisplayName("신청일도 없으면 조회 끝 날짜로 떨어진다")
    void 둘다없으면_끝날짜() {
        assertThat(DuffSalesImportService.tradeDateOf(null, null, TO)).isEqualTo(TO);
    }

    @Test
    @DisplayName("‼️지정한 날이 지난달이어도 그 날이다 — 끝 날짜로 바꿔치지 않는다")
    void 지정일이_과거여도_그대로() {
        // "지난달이 마감됐으면 거래일을 오늘로 지정해도 막힌다"의 반대 경우.
        // 지정값을 그대로 써야 담당자가 고른 달로 마감을 본다.
        LocalDate lastMonth = LocalDate.of(2026, 9, 5);
        assertThat(DuffSalesImportService.tradeDateOf(lastMonth, REQ, TO)).isEqualTo(lastMonth);
    }

    @Test
    @DisplayName("줄별 메모 — 원천 / 줄 / 공통 순으로 이어 붙인다")
    void 줄별메모() {
        // 근거: 프론트 실측 2026-10-23 §4② "레거시는 공통 메모와 별개로 줄마다 메모 칸이 있었다".
        assertThat(DuffSalesImportService.joinMemo("DSRE원천", "줄메모", "공통"))
                .isEqualTo("DSRE원천 / 줄메모 / 공통");

        // 빠진 칸은 구분자까지 같이 빠진다 — 빈 조각 때문에 ' / / ' 가 생기면 안 된다.
        assertThat(DuffSalesImportService.joinMemo("DSRE원천", null, "공통"))
                .isEqualTo("DSRE원천 / 공통");
        assertThat(DuffSalesImportService.joinMemo(null, "줄메모", null)).isEqualTo("줄메모");
        assertThat(DuffSalesImportService.joinMemo("  ", "줄메모", "  ")).isEqualTo("줄메모");

        // 전부 비면 null — 빈 문자열을 저장하면 "메모를 지웠다"와 구분이 안 된다.
        assertThat(DuffSalesImportService.joinMemo(null, null, null)).isNull();
        assertThat(DuffSalesImportService.joinMemo("", "  ", null)).isNull();
    }
}
