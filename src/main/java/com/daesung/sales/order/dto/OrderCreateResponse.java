package com.daesung.sales.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 신규 주문 등록 결과. */
@Schema(name = "OrderCreateResponse", description = "등록된 주문")
public record OrderCreateResponse(
        @Schema(description = "신청번호(REQ_CD) — DSRE2가 채번한다") int reqCd,

        @Schema(description = """
                진행상태 — 등록 직후는 항상 **A(접수완료)**다.
                레거시 신청도 STATE를 넣지 않고 DB 기본값에 맡긴다(같은 동작).""")
        String stateCode,

        @Schema(description = "진행상태명") String stateName,
        @Schema(description = "등록된 반 수") int classCount,
        @Schema(description = "등록된 과목수량 줄 수(간편신청만이면 0)") int subjectLineCount,
        @Schema(description = "총 신청 수량") long totalQty,

        @Schema(description = """
                함께 세운 매출번호(9/27 항목 2). 매출 직접입력을 안 보냈으면 **없다**.
                ‼️매출 등록이 실패하면 주문도 자동 취소되므로, 이 값이 없는데 주문이 있다면
                애초에 매출을 요청하지 않은 것이다.""")
        String salesNo
) {
}
