package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.List;

/**
 * 반품입고 요청(29p 물류 진입점). 물류가 반품 물량을 입고하면 한 트랜잭션으로
 * (1) 매출 반품(RETURN) 라인 자동 생성 + (2) 물류창고 재고 복구.
 * 담당자는 반품 '사실'(거래처·도서·수량·공급률)만 입력하고, 재고 잔량은 시스템이 자동 산출.
 */
public record ReturnInboundRequest(

        @Schema(description = "반품일자", example = "2026-06-22", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate returnDate,

        @Schema(description = "반품 거래처 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long partnerId,

        @Schema(description = "반품 입고될 물류창고 id(재고 +복구)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long warehouseId,

        @Schema(description = "반품 품목 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    /** 반품 품목. 금액=정가×공급률/100×수량, 세액=면세면 0 아니면 공급가액의 10%. */
    public record Item(

            @Schema(description = "상품 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long productId,

            @Schema(description = "정가(원)", example = "20000", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Positive Integer unitPrice,

            @Schema(description = "공급률(%)", example = "75", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @PositiveOrZero Integer supplyRate,

            @Schema(description = "반품수량", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,

            @Schema(description = "세액(선택). 미입력 시 0", example = "0")
            @PositiveOrZero Integer tax,

            @Schema(description = "원본 출고번호(역추적용, 선택)", example = "OUT-20260601-1")
            String sourceOutNo,

            @Schema(description = "비고", example = "6월 반품")
            String memo
    ) {
    }
}
