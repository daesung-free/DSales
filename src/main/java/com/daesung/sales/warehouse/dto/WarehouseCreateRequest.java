package com.daesung.sales.warehouse.dto;

import com.daesung.sales.warehouse.entity.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 창고 등록 요청 DTO. */
public record WarehouseCreateRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull WarehouseType type
) {
}
