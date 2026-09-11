package com.daesung.sales.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 재고 정책 설정.
 *
 * <p>★<b>음수 재고는 기본 허용이다</b> — 발주처 확정(2026-08-31 화면7 제품수불부) 원문:
 * <blockquote>
 * "재고 음수 차단 로직은 적용되면 안됩니다. 입고 전 출고되는 상품은 재고 (−)로 처리되며,
 *  DSRE에서 수불관리하는 상품도 출고 수량만 나타나 재고가 마이너스로 표시되는 게 정상입니다."
 * </blockquote>
 *
 * <p>‼️이 판단이 두 번 뒤집혔다. 2026-09-11 사내 점검에서 "폐기 초과가 통과된다"를 결함으로
 * 보고 차단을 넣었다가, 발주처 기준으로는 <b>통과가 정상</b>이라 다시 걷어냈다.
 * 음수를 막는 쪽으로 되돌리기 전에 반드시 위 원문을 확인할 것.
 *
 * <p>다만 무검증은 오타 하나로 999,999 폐기가 조용히 지나간다. 그래서 막지는 않되
 * <b>경고를 응답에 실어 보낸다</b>({@code warnings[]}). 경고 표시 방식은 발주처 회신 대기 중이다.
 *
 * @param blockNegativeStock 예외적으로 <b>막고 싶을 때만</b> true. 기본 false(=허용).
 */
@ConfigurationProperties(prefix = "daesung.inventory")
public record InventoryProperties(Boolean blockNegativeStock) {

    /** 음수 재고를 막는가. 기본은 <b>막지 않는다</b>(발주처 2026-08-31). */
    public boolean blockNegative() {
        return Boolean.TRUE.equals(blockNegativeStock);
    }
}
