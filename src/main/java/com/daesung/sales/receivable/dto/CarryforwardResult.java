package com.daesung.sales.receivable.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 채권 이월 스냅샷 생성 결과(idempotent 재생성). */
public record CarryforwardResult(
        @Schema(description = "이월 귀속 연도") int fiscalYear,
        @Schema(description = "삭제된 기존 스냅샷 수") int deleted,
        @Schema(description = "생성된 스냅샷 수(잔액≠0 거래처)") int generated,
        @Schema(description = "이월 총액") long totalCarryAmount
) {
}
