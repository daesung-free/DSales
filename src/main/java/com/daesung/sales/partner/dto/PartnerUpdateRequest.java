package com.daesung.sales.partner.dto;

import com.daesung.sales.partner.entity.PartnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** 거래처 수정 요청 DTO. 코드(code)는 불변이라 제외. 담보(여신)는 채권 담보비율 계산에 사용. */
public record PartnerUpdateRequest(

        @Schema(description = "거래처명", example = "리브커넥스(주)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "정산유형(NORMAL/CONSIGN)", example = "CONSIGN")
        PartnerType type,

        @Schema(description = "담보금액(여신한도)", example = "50000000")
        Long assureAmount,

        @Schema(description = "담보 만기", example = "2027-06-30")
        LocalDate assureExpiry,

        @Schema(description = "담보 내용(비고)", example = "부동산 근저당")
        String assureNote
) {
}
