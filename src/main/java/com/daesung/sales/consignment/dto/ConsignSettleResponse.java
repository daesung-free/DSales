package com.daesung.sales.consignment.dto;

import com.daesung.sales.consignment.entity.ConsignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 위탁 미결정산 결과. 확정된 매출 라인 + 정산 후 미결 잔여/상태.
 *
 * <p>한 줄에서 정산·반품을 함께 처리하므로 결과도 둘을 나눠 돌려준다
 * (발주처 확정: 정산수량은 매출 확정, 반품수량은 매출 무관·재고 복귀).
 */
public record ConsignSettleResponse(

        @Schema(description = "정산 결과 라인") List<Line> items
) {
    @Schema(name = "ConsignSettleLine")
    public record Line(
            @Schema(description = "확정 매출번호", example = "I-20260622-1") String salesNo,
            @Schema(description = "미결(consignment_out) id") Long consignmentOutId,
            @Schema(description = "위탁출고번호") String sourceOutNo,
            @Schema(description = "이번 정산수량(매출 확정분)") int settleQty,
            @Schema(description = "이번 반품수량(매출 무관, 재고 복귀분)") int returnQty,
            @Schema(description = "처리 후 미결 잔여") int remainingQty,
            @Schema(description = "처리 후 상태(PARTIAL/CLOSED)") ConsignmentStatus status,
            @Schema(description = "공급가액(정산분)") long supplyAmount,
            @Schema(description = "부가세(정산분)") long tax,
            @Schema(description = "합계금액(정산분)") long totalAmount,
            @Schema(description = "반품 후 물류창고 잔량(반품 없으면 0)") int mainBalance,
            @Schema(description = "반품 후 위탁창고 잔량(반품 없으면 0)") int consignBalance
    ) {
    }
}
