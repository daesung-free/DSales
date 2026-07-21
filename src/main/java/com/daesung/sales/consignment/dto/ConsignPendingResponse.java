package com.daesung.sales.consignment.dto;

import com.daesung.sales.consignment.entity.ConsignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 위탁 미결(잔여>0) 조회 결과. 정산 화면에서 거래처별로 불러온다. */
public record ConsignPendingResponse(

        @Schema(description = "위탁 거래처 id") Long partnerId,
        @Schema(description = "위탁 거래처명") String partnerName,
        @Schema(description = "미결 라인") List<Line> items
) {
    public record Line(
            @Schema(description = "미결(consignment_out) id") Long consignmentOutId,
            @Schema(description = "위탁출고번호", example = "OUT-20260608-1") String sourceOutNo,
            @Schema(description = "상품 id") Long productId,
            @Schema(description = "상품코드") String productCode,
            @Schema(description = "상품명") String productName,
            @Schema(description = "총 출고수량") int totalQty,
            @Schema(description = "누적 정산수량") int settledQty,
            @Schema(description = "미결 잔여수량") int remainingQty,
            @Schema(description = "상태(OPEN/PARTIAL)") ConsignmentStatus status
    ) {
    }
}
