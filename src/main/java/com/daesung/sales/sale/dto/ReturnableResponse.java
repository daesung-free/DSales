package com.daesung.sales.sale.dto;

import java.util.List;

/**
 * 교재식 반품 가능내역 조회 응답. 거래처의 도서×정가×공급률 단위 누적 출고내역.
 *
 * <p>정가·공급률은 <b>원 출고건 값을 표시해 주기 위한 참고값</b>이며 반품 등록 시 수정할 수 있다
 * (발주처 확정 2026-08-05: "공급률은 원 출고건 값을 기준으로 표시하되 담당자가 필요 시 수정 가능").
 * 반품 범위 판정은 <b>도서 단위 합계</b>로 한다.
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
