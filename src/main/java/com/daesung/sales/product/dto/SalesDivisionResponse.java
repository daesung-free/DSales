package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.MajorCategory;
import com.daesung.sales.product.entity.SalesDivision;
import io.swagger.v3.oas.annotations.media.Schema;

/** 세부구분 응답. 대분류는 코드와 한글명을 같이 준다 — 프론트가 매핑표를 들고 있지 않게. */
public record SalesDivisionResponse(
        @Schema(description = "id") Long id,
        @Schema(description = "세부구분 코드(불변)", example = "D모의고사") String code,
        @Schema(description = "세부구분 명칭", example = "D모의고사") String name,
        @Schema(description = "대분류 코드", example = "ETC_EXAM") MajorCategory majorCategory,
        @Schema(description = "대분류 명칭", example = "기타고사") String majorCategoryName,
        @Schema(description = "사용여부") boolean useYn,
        @Schema(description = "정렬 순서") int sortOrder
) {
    public static SalesDivisionResponse from(SalesDivision d) {
        return new SalesDivisionResponse(d.getId(), d.getCode(), d.getName(),
                d.getMajorCategory(), d.getMajorCategory().label(), d.isUseYn(), d.getSortOrder());
    }
}
