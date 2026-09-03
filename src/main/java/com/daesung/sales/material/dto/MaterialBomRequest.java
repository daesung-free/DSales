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
        @Positive int qtyPerSet,

        @Schema(description = """
                **회차 반복형** 여부(공통 자재에만 의미 있음).
                · `true` — 회차마다 반복 사용(OMR 등). 세트당수량이 '회차 수만큼 반영한 값'이므로
                  회차당 소요 = 세트당수량 ÷ 회차수. **회차만 단독으로 팔려도 자재가 나간다.**
                · `false`(기본) — 세트 전체에 한 번만 필요(해설강의쿠폰 등).
                  회차 단독 출고에는 붙지 않는다.
                ‼️이 값이 있어야 제품수불부 자재 상세의 '회차 단독 출고분'을 낼 수 있다 —
                숫자만으로는 `4`가 "4회차×1"인지 "세트당 4개 고정"인지 구분되지 않는다.""",
                example = "true")
        Boolean perRound
) {
}
