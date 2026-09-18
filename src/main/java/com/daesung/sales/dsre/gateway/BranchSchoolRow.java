package com.daesung.sales.dsre.gateway;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 지사에 딸린 학교/학원(DSRE2 {@code tbl_cust_ref}). 주문 등록의 {@code MGR_CD} 후보다.
 *
 * <p>지사를 고르면 그 지사가 담당하는 곳만 보여주기 위한 목록이다 —
 * 전국 학교 2,436곳을 다 보여주면 담당자가 고를 수 없다.
 */
@Schema(name = "BranchSchoolRow", description = "지사별 학교/학원")
public record BranchSchoolRow(
        @Schema(description = "학교/학원코드(MGR_CD) — 주문 등록에 쓰는 값") String schoolCode,
        @Schema(description = "학교/학원명") String schoolName,
        @Schema(description = "학교 여부(false면 학원)") boolean school
) {
}
