package com.daesung.sales.dsre.gateway;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 기간 물류비 집계 결과(출고 또는 회수). 근거: 레거시 물류비계산2.vb OutData(출고)/InData(회수).
 * <p>자재금액 공식은 출고·회수 공통으로 정본 4분류(PAPER/OMR/ETC/LABEL)로 통일한다.
 * (레거시 회수 InData 금액합계는 OMR을 PAPER단가로 계산하고 라벨을 제외하는 불일치가 있어 답습하지 않음 —
 * CLAUDE.md 재고/계산식 단일화 규율.)</p>
 * <p>인원비(laborAmount)는 <b>출고에만</b> 발생한다. 회수는 자재금액만이며 inwonTotal·laborAmount·reqCount=0.</p>
 */
public record PeriodLogisCost(
        @Schema(description = "집계 종류", example = "OUTBOUND", allowableValues = {"OUTBOUND", "RETURN"}) String kind,
        @Schema(description = "구분", example = "ALL", allowableValues = {"ALL", "NORMAL", "ACCIDENT"}) String mode,
        @Schema(description = "조회 시작일") LocalDate from,
        @Schema(description = "조회 종료일") LocalDate to,
        @Schema(description = "시험지 금액") long paperAmount,
        @Schema(description = "OMR 금액") long omrAmount,
        @Schema(description = "단행본/책자(ETC) 금액") long etcAmount,
        @Schema(description = "라벨 금액") long labelAmount,
        @Schema(description = "자재금액 합(= 시험지+OMR+ETC+라벨)") long materialAmount,
        @Schema(description = "인원 합(출고만, 신청 단위로 1회 산정)") int inwonTotal,
        @Schema(description = "인원비 합(출고만, Σ 신청별 인원×(BASIC+TRADE))") long laborAmount,
        @Schema(description = "물류비 합계(= 자재금액 + 인원비)") long total,
        @Schema(description = "집계 대상 신청 건수(출고만)") int reqCount
) {
    /** 출고 집계 결과 생성. */
    public static PeriodLogisCost outbound(LogisMode mode, LocalDate from, LocalDate to,
                                           long paper, long omr, long etc, long label,
                                           int inwonTotal, long labor, int reqCount) {
        long material = paper + omr + etc + label;
        return new PeriodLogisCost("OUTBOUND", mode.name(), from, to,
                paper, omr, etc, label, material, inwonTotal, labor, material + labor, reqCount);
    }

    /** 회수 집계 결과 생성(인원비 없음). */
    public static PeriodLogisCost ret(LogisMode mode, LocalDate from, LocalDate to,
                                      long paper, long omr, long etc, long label) {
        long material = paper + omr + etc + label;
        return new PeriodLogisCost("RETURN", mode.name(), from, to,
                paper, omr, etc, label, material, 0, 0L, material, 0);
    }
}
