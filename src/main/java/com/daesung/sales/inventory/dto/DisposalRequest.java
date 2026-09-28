package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/** 폐기 등록 요청. 등록 수량만큼 재고 즉시 차감(음수재고 방지). */
public record DisposalRequest(

        @Schema(description = "처리일자", example = "2026-06-26", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = "창고 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long warehouseId,

        @Schema(description = "폐기 품목", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    @Schema(name = "DisposalItem")
    public record Item(

            @Schema(description = "상품 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long productId,

            @Schema(description = "폐기 수량(양수)", example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,

            @Schema(description = """
                    비고. 화면의 '비고' 칸이 그대로 여기로 온다
                    (2026-09-28 화면 정리안 — "사유 드롭다운 삭제, 비고에 적으면 사유로 저장").
                    ‼️`memo` 로 보내도 같다 — 화면 라벨이 '비고'라 필드명이 `reason` 인 것이
                    헷갈릴 수 있어 둘 다 받는다.""",
                    example = "파본")
            String reason,

            @Schema(description = "비고(`reason` 과 같은 칸. 둘 다 오면 reason 이 우선)")
            String memo
    ) {
        /** 화면이 reason 으로 보내든 memo 로 보내든 같은 칸이다. */
        public String note() {
            return (reason != null && !reason.isBlank()) ? reason : memo;
        }
    }
}
