package com.daesung.sales.dsre.gateway;

import java.util.Arrays;
import java.util.Optional;

/**
 * 주문 진행상태. <b>DSRE2 {@code tbl_request_info.STATE}가 원본</b>이고 우리는 읽기만 한다
 * (발주처 확정 2026-08-11: "DSRE는 그대로 사용"). 상태 전이는 DSRE2 데스크톱이 수행한다.
 *
 * <p>코드값·한글명은 우리가 정한 것이 아니라 DSRE2 DDL 주석에 이미 정의되어 있던 것을 그대로 옮겼다 —
 * {@code STATE char default 'A' comment 'A : 접수완료, G : 상품검수, S : 상품 준비중,
 * W : 발송 준비중, D : 발송완료, C : 삭제(지사외 본사 및 물류에서 삭제시)'}.
 * 발주처 회신(자료요청서 3-2(가))의 상태명과 글자까지 일치한다.
 *
 * <p>⚠️ 취소는 주체에 따라 기록이 다르다. 본사·물류가 지우면 {@code STATE='C'}로 남지만,
 * 특약점(지사)이 지우면 행 자체가 DELETE되어 조회되지 않는다(order 사이트 BookUpdate_SQL 확인).
 * 따라서 "취소 건수"를 C로만 세면 지사 취소분이 빠진다.
 */
public enum OrderState {

    RECEIVED("A", "접수완료"),
    INSPECTING("G", "상품검수"),
    PREPARING("S", "상품준비중"),
    READY_TO_SHIP("W", "발송준비중"),
    SHIPPED("D", "발송완료"),
    CANCELED("C", "삭제");

    private final String code;
    private final String label;

    OrderState(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** DSRE2 코드값 → enum. 알 수 없는 값은 비어 있는 값으로 돌려준다(운영 데이터에 없는 값이 와도 조회는 살아 있어야 한다). */
    public static Optional<OrderState> ofCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String c = code.trim();
        return Arrays.stream(values()).filter(s -> s.code.equals(c)).findFirst();
    }

    /** 조회 결과 표시용 한글명. 미정의 코드는 코드값을 그대로 보여준다(무엇이 왔는지 감춰지지 않게). */
    public static String labelOf(String code) {
        return ofCode(code).map(OrderState::label).orElseGet(() -> code == null ? "" : code.trim());
    }
}
