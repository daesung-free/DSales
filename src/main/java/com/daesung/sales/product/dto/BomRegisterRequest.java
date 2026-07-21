package com.daesung.sales.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** BOM 구성 등록(완제품=path의 상품, 구성품 목록). 기존 구성은 대체됨. */
public record BomRegisterRequest(

        @Schema(description = "구성품 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Component> components
) {
    public record Component(

            @Schema(description = "구성품 상품 id", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long childProductId,

            @Schema(description = "BOM 비율(완제품 1개당 구성품 수)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int ratio
    ) {
    }
}
