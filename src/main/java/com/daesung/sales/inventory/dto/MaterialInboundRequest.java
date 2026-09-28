package com.daesung.sales.inventory.dto;

import com.daesung.sales.inventory.entity.InboundType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/**
 * 자재 입고 요청(03 입고/대체).
 *
 * <p>근거: 9/27 회의 항목 6 — "도서·수불 표시 안되게. <b>자재별로 입고 가능하게!!!</b>" ·
 * 항목 5 "dsre는 매출만 변경, 매출에서는 수불·자재 다 관리".
 *
 * <p>도서 입고({@link InboundRequest})와 <b>요청 모양을 일부러 똑같이</b> 맞췄다.
 * 같은 화면에서 품목 종류만 바꿔 쓰는 동작이라, 필드 이름이 다르면 화면이 두 벌이 된다.
 * 다른 것은 {@code productId} 자리에 {@code materialId} 가 온다는 것 하나다.
 */
public record MaterialInboundRequest(

        @Schema(description = "처리일자", example = "2026-06-08", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = "입고 거래처(인쇄소 등) id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long supplierClientId,

        @Schema(description = "도착 창고 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long destinationWarehouseId,

        @Schema(description = """
                입고구분. 미지정 시 NORMAL.
                자재는 되팔지 않으므로 실무상 대부분 NORMAL 이다 —
                `PURCHASE`(매입)는 16p 순매출조회 매입액에 잡히니 자재에는 쓰지 않는 것이 맞다.""",
                example = "NORMAL")
        InboundType inboundType,

        @Schema(description = "입고 품목 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    /** 입고 자재. */
    @Schema(name = "MaterialInboundItem")
    public record Item(

            @Schema(description = "자재 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long materialId,

            @Schema(description = "입고단가(원가, 원)", example = "120")
            Long unitCost,

            @Schema(description = "입고수량(양수)", example = "5000", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,

            @Schema(description = "비고", example = "1회 시험지 인쇄 납품")
            String memo
    ) {
    }
}
