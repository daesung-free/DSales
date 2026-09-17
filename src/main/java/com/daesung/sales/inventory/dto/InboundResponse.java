package com.daesung.sales.inventory.dto;

import com.daesung.sales.inventory.entity.InboundType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 입고 결과. 품목별 입고수량 + 갱신된 현재 잔량. */
public record InboundResponse(

        @Schema(description = "입고 전표번호(IN-yyyyMMdd-n). 취소할 때 이 번호로 지정한다",
                example = "IN-20260917-1")
        String inboundNo,

        @Schema(description = "도착 창고 id", example = "1")
        Long warehouseId,

        @Schema(description = "도착 창고명", example = "본사 메인 물류창고")
        String warehouseName,

        @Schema(description = "입고구분 NORMAL/PURCHASE", example = "NORMAL")
        InboundType inboundType,

        @Schema(description = "입고 결과 품목")
        List<Line> items
) {
    @Schema(name = "InboundLine")
    public record Line(

            @Schema(description = "상품 id", example = "1")
            Long productId,

            @Schema(description = "상품코드", example = "KOR-A02-1")
            String productCode,

            @Schema(description = "이번 입고 수량", example = "1000")
            int inboundQty,

            @Schema(description = "입고 후 현재 잔량", example = "1000")
            int currentQty
    ) {
    }
}
