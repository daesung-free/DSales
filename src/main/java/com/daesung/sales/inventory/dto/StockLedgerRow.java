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

        @Schema(description = """
                무상 상세 — IC 학생용(무상 세부구분 `학생용` × 대분류 IC).
                ★`free`/`teacher` 를 **다시 쪼갠 보조 칸**이다. 기존 두 칸의 뜻은 바뀌지 않았다 —
                정의를 바꾸면 순매출조회·외상매출현황과 숫자가 갈린다.""")
        long freeIcStudent,
        @Schema(description = "무상 상세 — IC 교사용(`교사용` × 대분류 IC)") long freeIc,
        @Schema(description = "무상 상세 — M+(무상 세부구분 `M+`)") long freeMplus,
        @Schema(description = """
                무상 상세 — 기타. ‼️**IC+ 가 여기로 떨어진다** — 레거시가 M+만 별도 칸으로 떼어냈다
                (제품수불부.vb:121). 의도인지 누락인지 코드로는 알 수 없어 그대로 뒀다.""")
        long freeEtc,
        @Schema(description = "재고실사 조정(순증감)") long adjust,
        @Schema(description = """
                순매출수량 = 매출 − 반품. **교사용·증정은 무가라 들어가지 않는다.**
                ★화면에서 직접 더하지 말 것 — 2026-09-11 점검에서 수불부 3,029(매출+교사용+반품) vs
                순매출조회 3,006(매출−반품)으로 갈렸다. 정본은 순매출조회 쪽이고 이 값이 그것과 같다.""")
        long netSaleQty,
        @Schema(description = "현재재고(마감)") long closing,
        @Schema(description = "캐시 잔량(inventory.qty)") long cachedBalance,
        @Schema(description = "이벤트합계=캐시 일치 여부") boolean reconciled
) {
}
