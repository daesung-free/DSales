package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.MajorCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 대분류 1건 + 그 아래 세부구분 목록. 32p 상품 등록이 "대분류 먼저 선택 → 하위 세부구분 입력"이라
 * 한 번 호출로 두 단계를 다 채울 수 있게 묶어서 준다.
 */
public record MajorCategoryResponse(
        @Schema(description = "대분류 코드", example = "ETC_EXAM") MajorCategory code,
        @Schema(description = "대분류 명칭", example = "기타고사") String name,
        @Schema(description = "하위 세부구분 목록") List<SalesDivisionResponse> divisions
) {
    public static MajorCategoryResponse of(MajorCategory c, List<SalesDivisionResponse> divisions) {
        return new MajorCategoryResponse(c, c.label(), divisions);
    }
}
