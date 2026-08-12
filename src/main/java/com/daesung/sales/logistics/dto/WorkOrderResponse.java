package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 작업요청서 1건 = 발송 건 + 그 안에 담을 도서 목록.
 *
 * <p>작업결과가 "다 됐나"를 보는 요약이라면, 작업요청서는 물류에게 <b>"무엇을 몇 개 넣어라"</b>를
 * 지시하는 목록이다(레거시 작업요청서.vb가 도서코드·수량·정가·공급률까지 뽑는다).
 */
@Schema(name = "WorkOrderResponse", description = "작업요청서(발송 건 + 도서 지시 목록)")
public record WorkOrderResponse(
        @Schema(description = "발송 건 id") Long id,
        @Schema(description = "분류") String tradeClass,
        @Schema(description = "거래일자") LocalDate tradeDate,
        @Schema(description = "거래처코드") String partnerCode,
        @Schema(description = "거래처명") String partnerName,
        @Schema(description = "학교코드") String schoolCode,
        @Schema(description = "학교명") String schoolName,
        @Schema(description = "출력 여부") boolean printed,
        @Schema(description = "박스 수") int boxCount,
        @Schema(description = "발송일") LocalDate sentDate,
        @Schema(description = "발송메모") String sendMemo,
        @Schema(description = "총 수량") long totalQty,
        @Schema(description = "도서별 지시 목록") List<Line> lines
) {
    @Schema(name = "WorkOrderLine")
    public record Line(
            @Schema(description = "분류코드") String catCode,
            @Schema(description = "도서코드") String productCode,
            @Schema(description = "도서명") String productName,
            @Schema(description = "회차") Integer bookRound,
            @Schema(description = "정가") Integer unitPrice,
            @Schema(description = "공급률(%)") Integer supplyRate,
            @Schema(description = "수량") long qty,
            @Schema(description = "금액(공급가)") long amount
    ) {
    }
}
