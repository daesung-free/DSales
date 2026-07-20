package com.daesung.sales.partner.dto;

import com.daesung.sales.partner.entity.PartnerType;
import jakarta.validation.constraints.NotBlank;

/** 거래처 등록 요청 DTO. type 미지정 시 NORMAL. */
public record PartnerCreateRequest(
        @NotBlank String code,
        @NotBlank String name,
        PartnerType type
) {
}
