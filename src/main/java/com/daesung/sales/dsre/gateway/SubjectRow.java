package com.daesung.sales.dsre.gateway;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 시행에 속한 <b>과목</b>(DSRE2 {@code tbl_resource_info}). 과목신청(간편신청이 아닌 경우)의 입력 단위.
 *
 * <p>{@code DISP_GN='Y'}(신청 시 계산처리 대상)인 것만 내려준다 —
 * 레거시 신청 화면과 같은 조건이다({@code Application_SQL.xml}).
 */
@Schema(name = "SubjectRow", description = "시행별 과목")
public record SubjectRow(
        @Schema(description = "과목코드(RES_CD)") int resCd,
        @Schema(description = "과목명") String resName,
        @Schema(description = "교시/그룹(0=없음)") String period,
        @Schema(description = "계열 9=공통 / 1=인문 / 2=자연") String series,
        @Schema(description = "정렬 순서") int sortKey
) {
}
