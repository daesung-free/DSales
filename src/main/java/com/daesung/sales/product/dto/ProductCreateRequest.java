package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 상품 등록 요청 DTO. useYn 미지정 시 true. */
public record ProductCreateRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull ContentType contentType,
        boolean set,
        Integer price,
        boolean taxFree,
        String grade,
        Boolean useYn
) {
    public boolean useYnOrDefault() {
        return useYn == null || useYn;
    }
}
