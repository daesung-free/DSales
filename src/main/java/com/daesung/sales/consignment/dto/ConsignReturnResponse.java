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
            @Schema(description = "반품 수량(요청값 — case2+case1의 합)") int returnQty,
            @Schema(description = "Case 2 — 미결잔여분 반품(매출 무관, 위탁창고→물류창고)") int returnCase2Qty,
            @Schema(description = """
                    Case 1 — 확정매출분 반품(미결잔여 초과분). 매출 반품 라인 생성 + 정산 누적 축소.
                    근거: 발주처 화면검토 확인요청서 2026-08-31.""") int returnCase1Qty,
            @Schema(description = "초과 경고(정상 처리는 null). 차단하지 않고 경고만 준다") String overWarning,
            @Schema(description = "반품 후 미결 잔여") int remainingQty,
            @Schema(description = "미결 상태") ConsignmentStatus status,
            @Schema(description = "물류창고 잔량(반품 복귀 후)") int mainBalance,
            @Schema(description = "위탁창고 잔량(반품 차감 후)") int consignBalance
    ) {
    }
}
