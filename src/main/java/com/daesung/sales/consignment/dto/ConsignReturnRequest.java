package com.daesung.sales.consignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/**
 * 위탁 반품(미정산분 반품). 미결(미판매) 잔여를 위탁창고→물류창고로 역-자동이고 + 미결원장 축소.
 * 판매완료(정산)분 반품은 반품입고(/sales/return-inbound, 매출반품)로 처리.
 */
public record ConsignReturnRequest(

        @Schema(description = "반품 처리일자", example = "2026-06-30", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = "반품 항목", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    @Schema(name = "ConsignReturnItem")
    public record Item(

            @Schema(description = "미결(consignment_out) id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long consignmentOutId,

            @Schema(description = "반품 수량(≤ 미결 잔여, 초과 시 409)", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int returnQty,

            @Schema(description = "메모") String memo
    ) {
    }
}
