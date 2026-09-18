package com.daesung.sales.dsre.gateway;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 물류 회수 작업비 <b>한 행</b>. 근거: 프론트 요청(2026-09-18) B-4 —
 * "회수는 기간 총계 하나뿐이라 출고처럼 행 명세로 볼 수 없습니다".
 *
 * <p>★<b>총계와 같은 규칙으로 금액을 낸다.</b> 자재구분으로 단가를 고르고
 * (OMR→OMR / 단행본·책자→ETC / 라벨→LABEL / 그 외→PAPER), 회수단가는
 * {@code tbl_logis_cost} 의 특수행({@code DTL_CD=0}) 최신값을 쓴다 —
 * 총계 쿼리와 한 글자도 다르지 않아야 <b>행 합 = 총계</b>가 성립한다.
 */
@Schema(name = "ReturnCostDetailRow", description = "회수 작업비 명세 1행")
public record ReturnCostDetailRow(
        @Schema(description = "회수일자") LocalDate returnDate,
        @Schema(description = "자재코드") int materialCode,
        @Schema(description = "자재명") String materialName,
        @Schema(description = "자재구분(OMR·단행본·책자·라벨·그 외=시험지)") String materialType,
        @Schema(description = "회수 구분 ACCIDENT(사고)/NORMAL(반품)") String mode,
        @Schema(description = "수량") int qty,
        @Schema(description = "적용 단가") int unitCost,
        @Schema(description = "금액(수량×단가)") long amount
) {
}
