package com.daesung.sales.consignment.dto;

import com.daesung.sales.consignment.entity.ConsignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 위탁 미결정산 결과. 확정된 매출 라인 + 정산 후 미결 잔여/상태. */
public record ConsignSettleResponse(

        @Schema(description = "정산 결과 라인") List<Line> items
) {
    public record Line(
            @Schema(description = "확정 매출번호", example = "I-20260622-1") String salesNo,
            @Schema(description = "미결(consignment_out) id") Long consignmentOutId,
            @Schema(description = "위탁출고번호") String sourceOutNo,
            @Schema(description = "이번 정산수량") int settleQty,
            @Schema(description = "정산 후 미결 잔여") int remainingQty,
            @Schema(description = "정산 후 상태(PARTIAL/CLOSED)") ConsignmentStatus status,
            @Schema(description = "공급가액") long supplyAmount,
            @Schema(description = "부가세") long tax,
            @Schema(description = "합계금액") long totalAmount
    ) {
    }
}
