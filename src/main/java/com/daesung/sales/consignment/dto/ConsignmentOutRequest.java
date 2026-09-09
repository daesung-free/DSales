package com.daesung.sales.consignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/** 위탁출고 등록. 물류창고 → 위탁창고 이고 + 미결원장(consignment_out) 생성. 매출 미발생. */
public record ConsignmentOutRequest(

        @Schema(description = "처리일자", example = "2026-06-08", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = "위탁 거래처 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long partnerId,

        @Schema(description = "출발(물류) 창고 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long fromWarehouseId,

        @Schema(description = "도착(위탁) 창고 id", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long toWarehouseId,

        @Schema(description = "위탁출고 품목", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    @Schema(name = "ConsignmentOutItem")
    public record Item(

            @Schema(description = "상품 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long productId,

            @Schema(description = "출고 수량(양수)", example = "1000", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,

            @Schema(description = """
                    정가(원). **미입력 시 도서 마스터 정가**를 쓴다.
                    출고 시점 값이 미결에 박혀 정산 화면의 공급가액 바탕이 된다.""", example = "10000")
            Integer unitPrice,

            @Schema(description = """
                    공급률(%). 미입력 시 **거래처×대분류 매핑 → 도서 기본공급률** 순으로 자동조회한다
                    (매출등록과 같은 우선순위).""", example = "70")
            Integer supplyRate,

            @Schema(description = "할인액(원). 미입력 시 거래처×대분류 매핑값. 있으면 공급률 대신 금액에 쓰인다")
            Integer discountAmount
    ) {
    }
}
