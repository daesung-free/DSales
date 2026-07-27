package com.daesung.sales.inventory.dto;

import com.daesung.sales.inventory.entity.InboundType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/** 일반 입고 요청. 인쇄소 등(거래처) → 도착 창고로 입고. */
public record InboundRequest(

        @Schema(description = "처리일자", example = "2026-06-08", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = "입고 거래처(인쇄소 등) id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long supplierClientId,

        @Schema(description = "도착 창고 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long destinationWarehouseId,

        @Schema(description = "입고구분: NORMAL(정상입고)/PURCHASE(매입입고, 외부콘텐츠 매입 — 16p 순매출조회 매입액에 반영). 미지정 시 NORMAL",
                example = "NORMAL")
        InboundType inboundType,

        @Schema(description = "입고 품목 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<InboundItem> items
) {
    /** 입고 품목. */
    public record InboundItem(

            @Schema(description = "상품 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long productId,

            @Schema(description = "입고단가(원가, 원)", example = "3500")
            Long unitCost,

            @Schema(description = "입고수량(양수)", example = "1000", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,

            @Schema(description = "비고", example = "초도 인쇄 물량")
            String memo
    ) {
    }
}
