package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 제품수불부 한 행(상품×창고). 재고 = inventory_txn 단일 공식 집계.
 * closing(현재재고) = opening + inbound + transfer + bom + dispose + sale + free + teacher + salesReturn + adjust.
 * 출고는 shipment_type으로 매출/무상/교사용/반품 분해(음수, 반품만 양수). 스펙 수불부 컬럼 대응.
 * reconciled = closing == cachedBalance(inventory.qty 캐시) — 이벤트 합계와 캐시 잔량 일치 여부.
 */
public record StockLedgerRow(
        @Schema(description = "상품 id") Long productId,
        @Schema(description = "상품코드") String productCode,
        @Schema(description = "상품명") String productName,
        @Schema(description = "창고 id") Long warehouseId,
        @Schema(description = "창고명") String warehouseName,
        @Schema(description = "이월(기간 시작 전 누계)") long opening,
        @Schema(description = "입고") long inbound,
        @Schema(description = "이고(순증감)") long transfer,
        @Schema(description = "조립/해체(순증감)") long bom,
        @Schema(description = "폐기(음수)") long dispose,
        @Schema(description = "매출출고(음수)") long sale,
        @Schema(description = "무상/증정(음수)") long free,
        @Schema(description = "교사용(음수)") long teacher,
        @Schema(description = "반품(양수)") long salesReturn,
        @Schema(description = "재고실사 조정(순증감)") long adjust,
        @Schema(description = "현재재고(마감)") long closing,
        @Schema(description = "캐시 잔량(inventory.qty)") long cachedBalance,
        @Schema(description = "이벤트합계=캐시 일치 여부") boolean reconciled
) {
}
