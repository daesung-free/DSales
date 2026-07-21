package com.daesung.sales.sale.dto;

import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 매출 등록 결과. 품목별 생성된 매출번호와 금액. */
public record SalesEntryResponse(

        @Schema(description = "거래처 id", example = "1")
        Long partnerId,

        @Schema(description = "거래처명", example = "리브커넥스(주)")
        String partnerName,

        @Schema(description = "등록된 매출 라인")
        List<Line> items
) {
    public record Line(

            @Schema(description = "매출번호(I)", example = "I-20260622-1001")
            String salesNo,

            @Schema(description = "상품 id", example = "1")
            Long productId,

            @Schema(description = "상품코드", example = "S2026A02")
            String productCode,

            @Schema(description = "출고유형", example = "NORMAL_SHIP")
            ShipmentType shipmentType,

            @Schema(description = "회계구분", example = "SALE")
            SalesCategory salesCategory,

            @Schema(description = "수량", example = "100")
            int qty,

            @Schema(description = "공급가액", example = "1500000")
            long supplyAmount,

            @Schema(description = "세액", example = "150000")
            long tax,

            @Schema(description = "총금액", example = "1650000")
            long totalAmount
    ) {
    }
}
