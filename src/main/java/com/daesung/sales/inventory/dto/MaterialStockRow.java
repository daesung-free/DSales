package com.daesung.sales.inventory.dto;

import com.daesung.sales.product.entity.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 자재 재고 현황 한 줄(V78).
 *
 * <p>‼️{@link MaterialLedgerRow}(제품수불부 자재 <b>상세</b>)와 다른 것이다.
 * 그쪽은 "세트가 팔리면서 자재가 몇 장 <b>쓰였나</b>"(소요량)이고,
 * 이쪽은 "그래서 지금 창고에 몇 장 <b>남았나</b>"(잔량)다.
 * 9/27 회의 전까지 우리에겐 앞의 것만 있었다.
 */
public record MaterialStockRow(

        @Schema(description = "자재 id") Long materialId,
        @Schema(description = "자재코드") String materialCode,
        @Schema(description = "자재명") String materialName,
        @Schema(description = "자재구분") MaterialType materialType,
        @Schema(description = "창고 id") Long warehouseId,
        @Schema(description = "창고명") String warehouseName,
        @Schema(description = "현재 잔량") int qty
) {
}
