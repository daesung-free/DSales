package com.daesung.sales.dsre.gateway;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 출고 물류비 계산 결과(신청 REQ 단위). 근거: 레거시 물류비계산2.vb OutData.
 * 자재금액 = Σ(시험지×PAPER + OMR×OMR + (단행본+책자)×ETC + 라벨×LABEL),
 * 인원비 = 인원 × (BASIC+TRADE), 합계 = 자재금액 + 인원비.
 */
public record OutboundLogisCost(
        @Schema(description = "신청번호(REQ_CD)") int reqCd,
        @Schema(description = "시험지 금액") long paperAmount,
        @Schema(description = "OMR 금액") long omrAmount,
        @Schema(description = "단행본/책자(ETC) 금액") long etcAmount,
        @Schema(description = "라벨 금액") long labelAmount,
        @Schema(description = "자재금액 합") long materialAmount,
        @Schema(description = "인원(PACKTYPE별 산정)") int inwon,
        @Schema(description = "인별 기본작업비 단가(BASIC)") int basicUnit,
        @Schema(description = "인별 배송비 단가(TRADE)") int tradeUnit,
        @Schema(description = "인원비(인원×(BASIC+TRADE))") long laborAmount,
        @Schema(description = "출고 물류비 합계") long total
) {
}
