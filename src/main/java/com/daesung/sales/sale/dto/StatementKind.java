package com.daesung.sales.sale.dto;

import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;

/**
 * 매출액 정리(15p)의 <b>구분</b> 필터. 근거: 정본 15p 핵심 요구사항 —
 * "구분 필터를 <b>통합조회 항목에 맞춰</b> 수정(전체/매출/반품/교사용/증정용)".
 *
 * <p>★<b>표준 4축과 값이 다른 것은 의도된 것이다.</b> 발주처 표준 '구분(상세)'는
 * 매출/무상/반품 3종인데, 여기는 무상을 <b>교사용·증정용으로 쪼갠</b> 5종이다.
 * 즉 회계구분과 출고유형이 섞인 축이다:
 * <pre>
 *   매출   → 회계구분 SALE
 *   반품   → 회계구분 RETURN
 *   교사용 → 출고유형 TEACHER_USE   ┐ 둘 다 회계로는 무상(FREE)이지만
 *   증정용 → 출고유형 GIFT          ┘ 이 화면은 나눠 봐야 한다
 * </pre>
 * 표준 축과 어긋나 보이지만 <b>화면별 요구가 더 구체적</b>이라 정본 15p를 따른다.
 * (교사용에 몇 부 나갔는지를 증정과 합쳐 놓으면 이 화면의 쓸모가 없어진다.)
 */
public enum StatementKind {

    SALE("매출", SalesCategory.SALE, null),
    RETURN("반품", SalesCategory.RETURN, null),
    TEACHER_USE("교사용", null, ShipmentType.TEACHER_USE),
    GIFT("증정용", null, ShipmentType.GIFT);

    private final String label;
    private final SalesCategory category;
    private final ShipmentType shipmentType;

    StatementKind(String label, SalesCategory category, ShipmentType shipmentType) {
        this.label = label;
        this.category = category;
        this.shipmentType = shipmentType;
    }

    public String label() {
        return label;
    }

    /** 회계구분으로 거를 값(출고유형으로 거르는 항목이면 null). */
    public SalesCategory category() {
        return category;
    }

    /** 출고유형으로 거를 값(회계구분으로 거르는 항목이면 null). */
    public ShipmentType shipmentType() {
        return shipmentType;
    }
}
