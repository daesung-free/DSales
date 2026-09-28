package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 자재 입출고 등록 결과. 전표번호와 품목별 처리 후 잔량. */
public record MaterialIoResponse(

        @Schema(description = "전표번호. 폐기·파손은 `P-`, 그 외는 `IN-`", example = "P-20260608-1")
        String refNo,
        @Schema(description = "입출고 구분", example = "OUTBOUND") String io,
        @Schema(description = "구분 표기", example = "출고") String ioLabel,
        @Schema(description = "창고 id") Long warehouseId,
        @Schema(description = "창고명") String warehouseName,
        @Schema(description = "처리 품목") List<Line> lines
) {
    @Schema(name = "MaterialIoLine")
    public record Line(
            @Schema(description = "자재 id") Long materialId,
            @Schema(description = "자재코드") String materialCode,
            @Schema(description = "자재명") String materialName,
            @Schema(description = "수량(양수)") int qty,
            @Schema(description = "처리 후 잔량") int currentQty
    ) {
    }
}
