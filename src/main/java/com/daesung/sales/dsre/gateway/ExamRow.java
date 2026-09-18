package com.daesung.sales.dsre.gateway;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 신청 가능한 <b>시행</b>(DSRE2 {@code tbl_product_dtl}). 주문 등록에서 무엇을 신청할지 고르는 축.
 *
 * <p>{@code dtlCd}가 곧 주문의 {@code DTL_CD}다 — 물류단가({@code tbl_logis_cost})가
 * 키로 쓰는 그 시행코드와 같은 값이다.
 */
@Schema(name = "ExamRow", description = "신청 가능 시행")
public record ExamRow(
        @Schema(description = "시행코드(DTL_CD)") int dtlCd,
        @Schema(description = "시행명") String dtlName,
        @Schema(description = "상품코드") String prodCode,
        @Schema(description = "상품명") String prodName,
        @Schema(description = "학년") String grade,
        @Schema(description = "성적처리 구분(Y/N)") String procYn,
        @Schema(description = "판매종료일(yyyyMMdd)") String saleEndDate,

        @Schema(description = """
                간편신청 가능 여부(Y/N). **시행마다 정해져 있다** —
                Y면 반별 인문·자연·통합 인원만 받고, N이면 과목별 수량을 받는다.
                화면이 이 값을 보고 입력칸을 고른다(우리가 고르는 값이 아니다).""")
        String easyYn
) {
}
