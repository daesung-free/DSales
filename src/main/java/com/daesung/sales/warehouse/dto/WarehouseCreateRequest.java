package com.daesung.sales.warehouse.dto;

import com.daesung.sales.warehouse.entity.WarehouseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 창고 등록 요청 DTO. */
public record WarehouseCreateRequest(

        @Schema(description = "창고코드(고유)", example = "WH-MAIN", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String code,

        @Schema(description = "창고명", example = "본사 메인 물류창고", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "창고유형(MAIN=물류/실물, CONSIGN=위탁/가상)", example = "MAIN", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull WarehouseType type,

        @Schema(description = "실물재고여부(미지정 시: MAIN=true, CONSIGN=false)", example = "true")
        Boolean physicalStock,

        @Schema(description = "소속거래처 id(위탁창고 1:1, 물류창고는 미지정)", example = "5")
        Long ownerClientId
) {
    /** 미지정 시 유형 기반 기본값(MAIN=실물, CONSIGN=가상). */
    public boolean physicalStockOrDefault() {
        return (physicalStock != null) ? physicalStock : type != WarehouseType.CONSIGN;
    }
}
