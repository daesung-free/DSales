package com.daesung.sales.partner.dto;

import com.daesung.sales.partner.entity.PartnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 거래처 등록 요청 DTO. type 미지정 시 NORMAL. */
public record PartnerCreateRequest(

        @Schema(description = "거래처코드(고유)", example = "P-LIB", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String code,

        @Schema(description = "거래처명2(합쳐진 풀네임)", example = "진주 이룸도서", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "도시명(예: 진주). DSRE CITY_NM", example = "진주")
        String cityName,

        @Schema(description = "거래처명1=상호만(예: 이룸도서). DSRE CUST_NM", example = "이룸도서")
        String name1,

        @Schema(description = "정산유형(NORMAL=정상판매, CONSIGN=후정산/위탁, 미지정 시 NORMAL)", example = "CONSIGN")
        PartnerType type
) {
}
