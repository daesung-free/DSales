package com.daesung.sales.receivable.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 채권 이월 스냅샷 생성 결과(idempotent 재생성). */
public record CarryforwardResult(
        @Schema(description = "이월 귀속 연도") int fiscalYear,
        @Schema(description = "삭제된 기존 스냅샷 수") int deleted,
        @Schema(description = "생성된 스냅샷 수(잔액≠0 거래처)") int generated,
        @Schema(description = "이월 총액") long totalCarryAmount,

        @Schema(description = """
                ★**재생성으로 값이 바뀐 거래처 수.** 처음 만들면 0이다.
                ‼️이미 만든 이월을 다시 만들었는데 여기가 0이 아니면,
                **스냅샷을 뜬 뒤에 전년 거래가 더 들어왔다**는 뜻이다.
                재생성 자체는 막지 않는다 — 막으면 늦게 들어온 거래를 영영 반영할 수 없다.
                대신 무엇이 바뀌었는지 알려 담당자가 확인하게 한다.""")
        int changed,

        @Schema(description = "재생성으로 달라진 이월 총액(이번 − 이전). 처음 만들면 0.")
        long changedAmount
) {
}
