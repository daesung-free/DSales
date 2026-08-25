package com.daesung.sales.logistics.dto;

import com.daesung.sales.logistics.entity.LogisCostManual;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 수기 물류작업비 응답(28p 에디팅). */
public record LogisCostManualResponse(
        @Schema(description = "id") Long id,
        @Schema(description = "접수일자") LocalDate reqDate,
        @Schema(description = "신청번호(0=대응 신청 없음)") int reqCd,
        @Schema(description = "상품코드") String productCode,
        @Schema(description = "상품명") String productName,
        @Schema(description = "학년") String grade,
        @Schema(description = "시행코드(0=없음)") int dtlCd,
        @Schema(description = "시행명") String detailName,
        @Schema(description = "거래처코드") String partnerCode,
        @Schema(description = "거래처명") String partnerName,
        @Schema(description = "자재 합") long materialQty,
        @Schema(description = "시험지 합") long paperQty,
        @Schema(description = "시험지 금액") long paperAmount,
        @Schema(description = "OMR 합") long omrQty,
        @Schema(description = "OMR 금액") long omrAmount,
        @Schema(description = "기타 합") long etcQty,
        @Schema(description = "기타 금액") long etcAmount,
        @Schema(description = "인원") int inwon,
        @Schema(description = "기본작업비") long basicAmount,
        @Schema(description = "출고비") long tradeAmount,
        @Schema(description = "금액합계(자재금액+기본작업비+출고비)") long totalAmount,
        @Schema(description = "구분") String applyGn,
        @Schema(description = "메모") String memo
) {
    public static LogisCostManualResponse from(LogisCostManual m) {
        return new LogisCostManualResponse(m.getId(), m.getReqDate(), m.getReqCd(),
                m.getProductCode(), m.getProductName(), m.getGrade(),
                m.getDtlCd(), m.getDetailName(), m.getPartnerCode(), m.getPartnerName(),
                m.getMaterialQty(), m.getPaperQty(), m.getPaperAmount(),
                m.getOmrQty(), m.getOmrAmount(), m.getEtcQty(), m.getEtcAmount(),
                m.getInwon(), m.getBasicAmount(), m.getTradeAmount(),
                m.toRow().totalAmount(), m.getApplyGn(), m.getMemo());
    }
}
