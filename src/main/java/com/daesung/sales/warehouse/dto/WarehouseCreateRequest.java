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
        @NotNull WarehouseType type
) {
}
