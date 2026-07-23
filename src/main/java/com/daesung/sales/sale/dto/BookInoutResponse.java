package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 도서입출고현황. 근거: 레거시 도서입출고현황.vb(매입+매출 이중장부 종합).
 * 매입측(입고/취소, inventory_txn 원가) + 매출측(출고/반품, sales 공급가) + 정본 재고 결합.
 */
public record BookInoutResponse(
        @Schema(description = "조회 시작일") LocalDate from,
        @Schema(description = "조회 종료일") LocalDate to,
        @Schema(description = "분류코드 필터(null=전체)") String catCode,
        @Schema(description = "도서 행 목록(도서코드 순)") List<Row> rows
) {
    /**
     * 도서 한 행. 취소=매입취소(INBOUND 역분개), 반품=매출반품.
     * 매출총이익=실판매금액−실매입금액(레거시가 '순매출금액'으로 오칭한 값).
     */
    public record Row(
            @Schema(description = "상품 id") Long productId,
            @Schema(description = "도서코드") String bookCode,
            @Schema(description = "도서명") String bookName,
            @Schema(description = "분류코드") String catCode,
            @Schema(description = "분류명") String catName,
            @Schema(description = "정가") Integer listPrice,
            // 매입측
            @Schema(description = "입고수량") long inboundQty,
            @Schema(description = "입고금액(매입원가)") long inboundAmount,
            @Schema(description = "취소(매입취소)수량") long cancelQty,
            @Schema(description = "취소금액") long cancelAmount,
            @Schema(description = "취소율(%, 취소÷입고×100, 입고0이면 null)") Double cancelRate,
            @Schema(description = "실매입수량(입고−취소)") long netPurchaseQty,
            @Schema(description = "실매입금액(입고−취소)") long netPurchaseAmount,
            // 매출측
            @Schema(description = "출고수량") long outboundQty,
            @Schema(description = "출고금액(공급가)") long outboundAmount,
            @Schema(description = "반품수량") long returnQty,
            @Schema(description = "반품금액") long returnAmount,
            @Schema(description = "반품률(%, 반품÷출고×100, 출고0이면 null)") Double returnRate,
            @Schema(description = "실판매수량(출고−반품)") long netSalesQty,
            @Schema(description = "실판매금액(출고−반품)") long netSalesAmount,
            // 재고·이익
            @Schema(description = "재고수량(정본, 종료일 기준 SUM(txn))") long stockQty,
            @Schema(description = "매출총이익(실판매금액−실매입금액)") long grossMargin
    ) {
    }
}
