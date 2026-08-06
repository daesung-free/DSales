package com.daesung.sales.logistics.dto;

import com.daesung.sales.logistics.entity.MaterialRate;
import com.daesung.sales.product.entity.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;

/** 자재 단가 응답. */
public record MaterialRateResponse(
        Long id,
        @Schema(description = "자재구분") MaterialType materialType,
        @Schema(description = "작업구분(0=공통)") int packType,
        @Schema(description = "자재 1개당 단가") int unitRate,
        @Schema(description = "비고") String memo
) {
    public static MaterialRateResponse from(MaterialRate r) {
        return new MaterialRateResponse(r.getId(), r.getMaterialType(), r.getPackType(),
                r.getUnitRate(), r.getMemo());
    }
}
