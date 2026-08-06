package com.daesung.sales.consignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 정산내역서(위탁정산 내역). 근거: 로직B + 위탁 회계기준 '정산 시점 매출'(재무팀 확정 2026-07-25).
 * 기간 내 위탁 부분정산 이력 + 연결된 매출 금액 + 미결원장 현황(총출고/기정산/미결잔여).
 */
public record SettlementStatementResponse(
        @Schema(description = "집계 시작일") LocalDate fromDate,
        @Schema(description = "집계 종료일") LocalDate toDate,
        @Schema(description = "정산 내역 행") List<Row> rows,
        @Schema(description = "합계") Summary summary
) {
    @Schema(name = "SettlementStatementRow")
    public record Row(
            @Schema(description = "정산일") LocalDate settledDate,
            @Schema(description = "거래처명") String partnerName,
            @Schema(description = "도서코드") String productCode,
            @Schema(description = "도서명") String productName,
            @Schema(description = "원본 위탁출고번호(OUT-)") String sourceOutNo,
            @Schema(description = "정산수량") long settleQty,
            @Schema(description = "매출번호(I-)") String salesRefNo,
            @Schema(description = "공급가액") long supplyAmount,
            @Schema(description = "세액") long tax,
            @Schema(description = "총금액") long totalAmount,
            @Schema(description = "총출고수량") long totalQty,
            @Schema(description = "누적 기정산수량") long settledQtyCum,
            @Schema(description = "미결 잔여수량") long remainingQty,
            @Schema(description = "미결 상태(OPEN/PARTIAL/CLOSED)") String status
    ) {
    }

    @Schema(name = "SettlementStatementSummary")

    public record Summary(
            @Schema(description = "정산 건수") long count,
            @Schema(description = "총 정산수량") long totalSettleQty,
            @Schema(description = "총 공급가액") long totalSupply,
            @Schema(description = "총 세액") long totalTax,
            @Schema(description = "총 금액") long totalAmount
    ) {
    }
}
