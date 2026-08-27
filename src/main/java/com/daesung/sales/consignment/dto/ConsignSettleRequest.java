package com.daesung.sales.consignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.List;

/**
 * 위탁 미결정산. 미결(consignment_out)을 부분/전량 정산 → 매출 확정.
 * 재고는 위탁출고 시 이미 이동됐고, 정산은 재무장부(매출)만 확정한다.
 *
 * <p><b>한 줄에서 정산과 반품을 함께 입력한다.</b> 근거: 발주처 확정(확인요청서 v1 2번 No.4,
 * 이슈#43, 2026-07-29) — "위탁출고내역 호출 그리드에 현재 '정산수량' 컬럼만 있는데
 * 여기에 '반품수량' 칼럼을 추가하면 될 것 같습니다. 정산수량에 입력하면 기존과 동일하게
 * 매출이 확정되고, 반품수량에 입력하면 매출 영향 없이 미결 잔여수량만 차감되고
 * 실물재고가 증가하는 방식".
 *
 * <p>두 API로 나누면 화면의 저장 한 번이 <b>두 트랜잭션</b>으로 갈라져, 한쪽만 성공하는 상태가
 * 생긴다. 담당자는 한 그리드에서 한 번 저장했는데 정산만 되고 반품은 빠지는 식이다.
 */
public record ConsignSettleRequest(

        @Schema(description = "매출일자", example = "2026-06-22", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate salesDate,

        @Schema(description = "처리 항목(정산수량·반품수량)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Settlement> settlements,

        @Schema(description = """
                이 확정에 사용한 임시저장 번호(선택). 넘기면 확정 성공 후 그 초안이 정리된다.
                ★없는 번호여도 확정은 실패하지 않는다 — 매출은 이미 섰는데 초안이 없다고
                전체를 되돌리면 손해가 더 크다.""", example = "DRAFT-20260622-1")
        String fromDraftId
) {
    @Schema(name = "ConsignSettlement")
    public record Settlement(

            @Schema(description = "미결(consignment_out) id", example = "1",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long consignmentOutId,

            @Schema(description = """
                    정산수량 — 매출을 확정한다(Case: 실제 판매분).
                    반품수량과 합쳐 미결 잔여를 넘으면 409.""", example = "300")
            @PositiveOrZero Integer settleQty,

            @Schema(description = """
                    반품수량 — **매출에 영향 없이** 미결 잔여만 차감하고 실물재고가 돌아온다
                    (위탁창고 → 물류창고). 미결 잔여분에 대한 반품(Case 2)이다.
                    이미 매출로 확정된 분의 반품(Case 1)은 이 칸이 아니라 반품입고로 처리한다.""",
                    example = "20")
            @PositiveOrZero Integer returnQty,

            @Schema(description = "정가(단가). 정산수량이 있을 때 필수", example = "20000")
            Integer unitPrice,

            @Schema(description = "공급률(%). 정산수량이 있을 때 필수", example = "75")
            Integer supplyRate,

            @Schema(description = "세액(선택). 미입력 시 0", example = "0")
            @PositiveOrZero Integer tax,

            @Schema(description = "메모") String memo
    ) {
        public int settleQtyOrZero() {
            return (settleQty == null) ? 0 : settleQty;
        }

        public int returnQtyOrZero() {
            return (returnQty == null) ? 0 : returnQty;
        }
    }
}
