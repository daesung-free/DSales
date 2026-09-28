package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/**
 * 자재 폐기 요청.
 *
 * <p>‼️<b>사유 칸을 두지 않는다.</b> 9/27 회의 A-3 — 폐기·통합매출·작업요청서에서
 * 사유 입력을 없애기로 확정됐다(항목 9 "폐기등록 … 사유입력 없애고 드롭다운 없애고").
 * 대신 비고는 남긴다 — 사유를 강제하지 않는 것과 메모를 못 쓰게 하는 것은 다르다.
 */
public record MaterialDisposalRequest(

        @Schema(description = "처리일자", example = "2026-06-08", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = "폐기 창고 id", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long warehouseId,

        @Schema(description = "폐기 자재 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    @Schema(name = "MaterialDisposalItem")
    public record Item(
            @Schema(description = "자재 id", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long materialId,
            @Schema(description = "폐기수량(양수로 넣는다 — 원장에는 음수로 기록된다)",
                    example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,
            @Schema(description = "비고", example = "인쇄 불량") String memo
    ) {
    }
}
