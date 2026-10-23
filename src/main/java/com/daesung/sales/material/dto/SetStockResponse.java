package com.daesung.sales.material.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 세트 가용재고 — <b>구성 회차별 시험지 재고의 최소값</b>.
 *
 * <p>근거: 발주처 확정(2026-10-23) —
 * "세트 재고는 구성 회차별 시험지 재고의 최소값. 예) 1~4회 100/120/105/111 → 세트 100.
 * 회차 단독판매도 동일 기준."
 *
 * <p>★<b>세트는 따로 쌓아 두는 물건이 아니다.</b> 회차 시험지를 모아 만드는 것이라,
 * "몇 세트 나갈 수 있나"는 <b>가장 모자란 회차</b>가 정한다. 100·120·105·111 이면
 * 120 짜리가 아무리 많아도 100 세트까지다.
 *
 * <p>‼️재고가 <b>음수</b>인 회차가 있으면 세트도 음수로 낸다. 0으로 올려 보여주면
 * "만들 수 있다"로 읽혀, 모자란 사실이 묻힌다(재고 음수를 막지 않기로 한 것과 같은 결).
 */
@Schema(name = "SetStockResponse", description = "세트 가용재고(구성 회차 최소값)")
public record SetStockResponse(

        @Schema(description = "세트 상품 id") Long setProductId,
        @Schema(description = "세트 상품코드") String setProductCode,
        @Schema(description = "세트 상품명") String setProductName,

        @Schema(description = """
                만들 수 있는 세트 수 = 구성 회차별 `가능수량` 의 **최소값**.
                구성이 없으면 null — 0과 다르다. 0은 "못 만든다", null 은 "구성이 안 잡혀 있다"다.""")
        Integer availableSets,

        @Schema(description = "회차별 내역 — 어느 회차가 모자라 세트가 막혔는지 보인다")
        List<Round> rounds
) {
    @Schema(name = "SetStockRound", description = "세트 구성 회차 1건")
    public record Round(
            @Schema(description = "회차 상품 id. 공통 자재(회차 없음)면 null") Long roundProductId,
            @Schema(description = "회차 상품코드. 공통이면 '(공통)'") String roundProductCode,
            @Schema(description = "회차명") String roundProductName,
            @Schema(description = "시험지 자재 id") Long materialId,
            @Schema(description = "시험지 자재코드") String materialCode,
            @Schema(description = "시험지 자재명") String materialName,
            @Schema(description = "현재 시험지 재고") int stockQty,
            @Schema(description = "세트당 소요수량") int qtyPerSet,
            @Schema(description = "이 회차로 만들 수 있는 세트 수 = 재고 ÷ 소요수량(버림)") int possibleSets,
            @Schema(description = "이 회차가 세트 수를 정하고 있나(= 최소값)") boolean bottleneck
    ) {
    }
}
