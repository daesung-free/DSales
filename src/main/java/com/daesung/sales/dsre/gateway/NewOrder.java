package com.daesung.sales.dsre.gateway;

import java.util.List;

/**
 * 게이트웨이로 넘기는 신규 주문(원장 형태). API DTO를 그대로 넘기지 않는다 —
 * 게이트웨이는 DSRE2 컬럼만 알면 되고, 검증·기본값은 서비스가 이미 끝낸 상태로 온다.
 */
public record NewOrder(
        int dtlCd,
        String custCode,
        String schoolCode,
        String procYn,
        String procYn2,
        String procDate,
        String teacher,
        String tel,
        String email,
        String zipCode,
        String address,
        String memo,
        String deliveryGubun,
        List<ClassLine> classes
) {
    /** 반 1개. {@code applyType} S=간편(인원) / N=과목(수량). */
    public record ClassLine(
            String className,
            String applyType,
            Integer humanities,
            Integer science,
            Integer combined,
            List<SubjectQty> subjects
    ) {
    }

    public record SubjectQty(int resCd, int qty) {
    }
}
