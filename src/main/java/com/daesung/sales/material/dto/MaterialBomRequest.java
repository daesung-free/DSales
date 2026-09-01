package com.daesung.sales.material.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 세트·회차 ↔ 자재 매칭 등록·수정 요청. */
public record MaterialBomRequest(

        @Schema(description = "자재 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long materialId,

        @Schema(description = """
                회차 상품 id. **비우면 '공통'** — 세트 전체에 붙는 범용 자재(OMR·라벨 등).
                시험지·해설지처럼 회차 전용 자재는 회차를 지정한다.""", example = "12")
        Long roundProductId,

        @Schema(description = """
                세트당 소요수량. **세트 내 회차 반복까지 고려한 최종 수량**을 넣는다 —
                4회차 구성에서 회차마다 쓰는 OMR은 4, 세트에 한 번만 필요한 쿠폰은 1.
                ‼️서버가 회차 수를 곱하지 않는다(발주처 확정).""",
                example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
        @Positive int qtyPerSet
) {
}
