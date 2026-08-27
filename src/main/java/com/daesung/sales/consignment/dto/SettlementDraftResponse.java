package com.daesung.sales.consignment.dto;

import com.daesung.sales.consignment.entity.SettlementDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 위탁정산 임시저장(초안). 필드 구성은 화면의 {@code SettlementDraft} 타입 그대로다 —
 * 화면이 이미 이 형태로 목업을 돌리고 있어, 서버가 맞춰 주면 스위치만 켜면 된다.
 *
 * <p>★<b>매출 미반영</b>이다. 이 응답에 담긴 수량·금액은 어디에도 확정되지 않았다.
 */
public record SettlementDraftResponse(

        @Schema(description = "초안번호(화면의 draftId)", example = "DRAFT-20260622-1") String draftId,
        @Schema(description = "저장 시각") LocalDateTime savedAt,
        @Schema(description = "매출 인식일") LocalDate salesDate,
        @Schema(description = "초안 상세행") List<Line> lines,
        @Schema(description = "정산수량 합") int totalQty,
        @Schema(description = "총금액 합") long totalAmount,
        @Schema(description = "메모") String memo
) {
    @Schema(name = "SettlementDraftLine")
    public record Line(
            @Schema(description = "미결 id(화면의 pendingId)") Long pendingId,
            @Schema(description = "위탁출고번호", example = "OUT-20260608-1") String outNo,
            @Schema(description = "거래처명") String custName,
            @Schema(description = "분류명") String catName,
            @Schema(description = "도서명") String bookName,
            @Schema(description = "이번에 정산할 수량") int settleQty,
            @Schema(description = "정가") Integer unitPrice,
            @Schema(description = "공급률(%)") Integer supplyRate,
            @Schema(description = "공급가액") long amount,
            @Schema(description = "세액") long tax,
            @Schema(description = "총금액") long total,
            @Schema(description = """
                    저장 시점의 미결 잔여. 확정 전에 다른 담당자가 같은 미결을 정산했으면
                    이 값과 지금 잔여가 다르다 — 화면이 그 차이를 보여줄 수 있게 함께 낸다.""")
            int remainingAtSave
    ) {
    }

    public static SettlementDraftResponse from(SettlementDraft d) {
        List<Line> lines = d.getLines().stream()
                .map(l -> new Line(
                        l.getConsignmentOut().getId(),
                        l.getConsignmentOut().getSourceOutNo(),
                        l.getConsignmentOut().getPartner().getName(),
                        l.getConsignmentOut().getProduct().getCatName(),
                        l.getConsignmentOut().getProduct().getName(),
                        l.getSettleQty(), l.getUnitPrice(), l.getSupplyRate(),
                        l.getSupplyAmount(), l.getTax(), l.getTotalAmount(),
                        l.getConsignmentOut().getRemainingQty()))
                .toList();
        return new SettlementDraftResponse(d.getDraftNo(), d.getCreatedAt(), d.getSalesDate(),
                lines, d.getTotalQty(), d.getTotalAmount(), d.getMemo());
    }
}
