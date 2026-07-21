package com.daesung.sales.partner.dto;

import com.daesung.sales.partner.entity.PartnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 거래처 수정 요청 DTO. 코드(code)는 불변이라 제외. */
public record PartnerUpdateRequest(

        @Schema(description = "거래처명", example = "리브커넥스(주)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "정산유형(NORMAL/CONSIGN)", example = "CONSIGN")
        PartnerType type
) {
}
