package com.daesung.sales.sale.dto;

import java.util.List;

/**
 * 교재식 반품 가능내역 조회 응답. 거래처의 도서×정가×공급률별 반품 가능수량.
 * 반품 시 이 목록에서 라인을 골라 그 범위 내에서만 반품(공급률·정가는 원 출고건 고정).
 */
public record ReturnableResponse(Long partnerId, List<Row> rows) {

    public record Row(
            Long productId,
            String productCode,
            String productName,
            Integer unitPrice,
            Integer supplyRate,
            long shippedQty,     // 누적 판매출고
            long returnedQty,    // 기(旣)반품
            long returnableQty   // 반품가능 = 출고 − 기반품
    ) {
    }
}
