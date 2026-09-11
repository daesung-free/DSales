package com.daesung.sales.partner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 거래처 DSRE2 동기화 결과.
 *
 * <p>★<b>무엇을 안 건드렸는지도 같이 알린다.</b> 동기화는 "덮어쓰는" 작업이라,
 * 담당자가 자기 입력값이 살아 있는지 확인할 수 없으면 다음부터 버튼을 안 누른다.
 */
@Schema(name = "ClientSyncResult", description = "거래처 동기화 결과(보존형)")
public record ClientSyncResult(
        @Schema(description = "DSRE2에서 읽은 행 수") int read,
        @Schema(description = "새로 만든 거래처") int added,
        @Schema(description = "기존 거래처에 DSRE 항목만 덮어쓴 수") int updated,
        @Schema(description = "DSRE2에 없어 손대지 않은 우리 쪽 거래처(수기 등록분)") int keptLocal,
        @Schema(description = "만료지사(END_GUBUN='Y')로 들어와 종료일이 찍힌 수") int expired
) {
}
