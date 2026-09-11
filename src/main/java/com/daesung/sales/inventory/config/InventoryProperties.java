package com.daesung.sales.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 재고 정책 설정.
 *
 * <p>★{@code allowNegativeStock} 은 <b>지시가 둘로 갈려 있어</b> 설정으로 뺀 값이다.
 *
 * <ul>
 *   <li><b>발주처</b>(화면검토 확인요청서 2026-08-31): "재고 음수 차단 로직은 적용되면 안됩니다.
 *       입고 전 출시/출고되는 상품의 경우 재고 (–)로 처리되며 … 마이너스로 표시되는게 정상입니다."</li>
 *   <li><b>개발팀 점검</b>(2026-09-11): 현재고 5,006인 도서를 99,999 폐기해도 통과해 잔량이
 *       −94,993이 됐다. 서버가 막아야 한다.</li>
 * </ul>
 *
 * <p>둘 다 일리가 있다 — 앞은 <b>업무상 정상인 음수</b>를, 뒤는 <b>오입력으로 생긴 음수</b>를
 * 말한다. 데이터만 봐서는 둘이 구분되지 않는다는 게 문제의 본질이다.
 * 어느 쪽으로 확정되든 코드를 고치지 않고 뒤집을 수 있게 설정으로 둔다.
 *
 * <p>현재 기본값은 <b>차단(false)</b> — 발주처 재확인 시 {@code true}로 바꾸면 종전대로 허용된다.
 */
@ConfigurationProperties(prefix = "daesung.inventory")
public record InventoryProperties(Boolean allowNegativeStock) {

    /** 미설정이면 차단. */
    public boolean allowNegative() {
        return Boolean.TRUE.equals(allowNegativeStock);
    }
}
