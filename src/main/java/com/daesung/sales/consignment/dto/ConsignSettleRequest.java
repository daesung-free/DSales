package com.daesung.sales.consignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/**
 * 위탁 미결정산. 미결(consignment_out)을 부분/전량 정산 → 매출 확정.
 * 재고는 위탁출고 시 이미 이동됨 — 정산은 재무장부(매출)만 확정한다.
 */
public record ConsignSettleRequest(

        @Schema(description = "매출일자", example = "2026-06-22", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate salesDate,

        @Schema(description = "정산 항목", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Settlement> settlements
) {
    public record Settlement(

            @Schema(description = "미결(consignment_out) id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long consignmentOutId,

            @Schema(description = "정산 수량(≤ 미결 잔여, 초과 시 409)", example = "300", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int settleQty,

            @Schema(description = "정가(단가)", example = "20000", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Integer unitPrice,

            @Schema(description = "공급률(%)", example = "75", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Integer supplyRate,

            @Schema(description = "메모") String memo
    ) {
    }
}
