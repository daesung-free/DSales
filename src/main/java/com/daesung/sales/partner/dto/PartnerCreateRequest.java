package com.daesung.sales.partner.dto;

import com.daesung.sales.partner.entity.PartnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 거래처 등록 요청 DTO. type 미지정 시 NORMAL. */
public record PartnerCreateRequest(

        @Schema(description = "거래처코드(고유)", example = "P-LIB", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String code,

        @Schema(description = "거래처명", example = "리브커넥스(주)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "정산유형(NORMAL=정상판매, CONSIGN=후정산/위탁, 미지정 시 NORMAL)", example = "CONSIGN")
        PartnerType type
) {
}
