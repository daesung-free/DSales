package com.daesung.sales.warehouse.dto;

import com.daesung.sales.warehouse.entity.WarehouseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 창고 수정 요청 DTO. 코드(code)는 불변이라 제외. */
public record WarehouseUpdateRequest(

        @Schema(description = "창고명", example = "본사 메인 물류창고", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "창고유형(MAIN/CONSIGN)", example = "MAIN", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull WarehouseType type,

        @Schema(description = "실물재고여부", example = "true")
        Boolean physicalStock,

        @Schema(description = "사용여부(31p). 미지정 시 true — false면 목록에서 숨김", example = "true")

        Boolean useYn,

        

        @Schema(description = "비고(31p)")

        String memo,

        @Schema(description = "소속거래처 id(위탁창고 1:1, 없으면 미지정)", example = "5")
        Long ownerClientId
) {
}
