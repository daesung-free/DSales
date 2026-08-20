package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.MajorCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 세부구분 등록·수정 요청. 수정 시 code는 무시된다(불변 키). */
public record SalesDivisionRequest(

        @Schema(description = "세부구분 코드(등록 시 필수, 이후 불변). 상품이 이 값으로 연결된다",
                example = "D모의고사", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 30)
        String code,

        @Schema(description = "세부구분 명칭(표시용, 변경 가능)", example = "D모의고사",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 50)
        String name,

        @Schema(description = """
                소속 대분류(집계 기준). MOCK_EXAM(모의고사)·TEXTBOOK(교재)·ETC_EXAM(기타고사)\
                ·SPECIAL_LECTURE(특강)·ETC(기타). IC는 미사용(숨김)이라 새로 지정하지 않는다""",
                example = "ETC_EXAM", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        MajorCategory majorCategory,

        @Schema(description = "사용여부. 미지정 시 수정에서는 기존값 유지, 등록에서는 true", example = "true")
        Boolean useYn,

        @Schema(description = "화면 정렬 순서(작을수록 위)", example = "3")
        Integer sortOrder
) {
}
