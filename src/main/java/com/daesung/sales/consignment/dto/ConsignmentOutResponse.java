package com.daesung.sales.consignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 위탁출고 등록 결과. 생성된 미결(consignment_out) + 이고 결과. */
public record ConsignmentOutResponse(

        @Schema(description = "위탁 거래처 id") Long partnerId,
        @Schema(description = "위탁 거래처명") String partnerName,
        @Schema(description = "출발(물류) 창고 id") Long fromWarehouseId,
        @Schema(description = "도착(위탁) 창고 id") Long toWarehouseId,
        @Schema(description = "생성된 미결 라인") List<Line> items
) {
    public record Line(
            @Schema(description = "미결(consignment_out) id") Long consignmentOutId,
            @Schema(description = "위탁출고번호", example = "OUT-20260608-1") String sourceOutNo,
            @Schema(description = "상품 id") Long productId,
            @Schema(description = "상품코드") String productCode,
            @Schema(description = "출고 수량") int qty,
            @Schema(description = "출발창고 잔량") int fromBalance,
            @Schema(description = "위탁창고 잔량") int toBalance
    ) {
    }
}
