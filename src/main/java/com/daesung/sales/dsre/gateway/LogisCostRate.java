package com.daesung.sales.dsre.gateway;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 물류단가 1행(DSRE2 tbl_logis_cost). 근거: 레거시 물류비용등록.vb.
 * dtl_cd(시행코드)를 키로 관리. dtl_cd=0은 회수 단가 특수행(PAPER/OMR/ETC만 사용).
 */
public record LogisCostRate(
        @Schema(description = "시행코드(DTL_CD, 0=회수단가)") int dtlCd,
        @Schema(description = "시험지 단가(OMR외)") int paper,
        @Schema(description = "OMR 단가") int omr,
        @Schema(description = "단행본(ETC) 단가") int etc,
        @Schema(description = "라벨 단가") int label,
        @Schema(description = "인별 기본작업비") int basic,
        @Schema(description = "인별 배송비(출고비)") int trade,
        @Schema(description = "포장구분(1:반별봉투 2:개별봉투 3:예외SET)") int packtype,
        @Schema(description = "여분포함 여부(Y/N)") String bSpare
) {
}
