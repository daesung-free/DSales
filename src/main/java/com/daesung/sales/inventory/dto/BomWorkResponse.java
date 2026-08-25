package com.daesung.sales.inventory.dto;

import com.daesung.sales.inventory.entity.BomDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** BOM 작업 결과. 완제품/구성품별 증감(delta)과 작업 후 잔량(balance). */
public record BomWorkResponse(
        @Schema(description = "작업 창고 id") Long warehouseId,
        @Schema(description = "작업 창고명") String warehouseName,
        @Schema(description = "방향") BomDirection direction,
        @Schema(description = "완제품 결과") Line parent,
        @Schema(description = "작업수량(완제품 기준)") int workQty,
        @Schema(description = "구성품 결과") List<Line> components
) {
    @Schema(name = "BomWorkLine")
    public record Line(
            @Schema(description = "상품 id") Long productId,
            @Schema(description = "상품코드") String productCode,
            @Schema(description = "상품명") String productName,
            @Schema(description = "BOM 비율(구성품만). 완제품 1개에 몇 개 들어가는가") Integer ratio,
            @Schema(description = "자재구분(구성품만) EXAM_PAPER/ANSWER_SHEET/OMR/LABEL/ETC") String materialType,
            @Schema(description = "물류비용 연계 작업구분(PACKTYPE). 36p 단가와 잇는 축") Integer packType,
            @Schema(description = "증감(+/-)") int delta,
            @Schema(description = "작업 후 잔량") int balance
    ) {
    }
}
