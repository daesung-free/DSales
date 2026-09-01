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
            @Schema(description = "이번 반품수량(요청값 그대로 — 아래 case2+case1의 합)") int returnQty,
            @Schema(description = """
                    Case 2 — **미결잔여분 반품** 수량. 매출 영향 없이 미결만 줄고
                    실물이 위탁창고→물류창고로 돌아온다.""") int returnCase2Qty,
            @Schema(description = """
                    Case 1 — **확정매출분 반품** 수량. 반품수량이 미결잔여를 넘으면 그 초과분이 여기 잡힌다.
                    매출 반품(RETURN) 라인이 생기고(매출 마이너스), 정산 누적이 줄며,
                    실물은 물류창고로 복구된다. 기존 정산 건은 소급 수정하지 않는다.
                    근거: 발주처 화면검토 확인요청서 2026-08-31.""") int returnCase1Qty,
            @Schema(description = """
                    초과 경고. 정산+반품이 미결 잔여를 넘었을 때 사유 문구가 담긴다(정상 처리는 null).
                    ★차단하지 않는다 — 발주처 확정(2026-08-31)에 따라 경고만 주고 등록은 진행한다.""")
            String overWarning,
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
