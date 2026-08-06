package com.daesung.sales.consignment.dto;

import com.daesung.sales.consignment.entity.ConsignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 위탁 반품 결과. 역-자동이고(위탁→물류) + 미결원장 축소 결과. */
public record ConsignReturnResponse(
        @Schema(description = "반품 처리 라인") List<Line> items
) {
    @Schema(name = "ConsignReturnLine")
    public record Line(
            @Schema(description = "미결(consignment_out) id") Long consignmentOutId,
            @Schema(description = "위탁출고번호(OUT-)") String sourceOutNo,
            @Schema(description = "상품코드") String productCode,
            @Schema(description = "반품 수량") int returnQty,
            @Schema(description = "반품 후 미결 잔여") int remainingQty,
            @Schema(description = "미결 상태") ConsignmentStatus status,
            @Schema(description = "물류창고 잔량(반품 복귀 후)") int mainBalance,
            @Schema(description = "위탁창고 잔량(반품 차감 후)") int consignBalance
    ) {
    }
}
