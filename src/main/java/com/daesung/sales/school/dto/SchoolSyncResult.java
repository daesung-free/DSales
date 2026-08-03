package com.daesung.sales.school.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 학교관리 DSRE 동기화 결과 요약(35p). */
@Schema(description = "학교 동기화 결과")
public record SchoolSyncResult(

        @Schema(description = "DSRE에서 읽은 매핑 행 수", example = "1520")
        int fetched,

        @Schema(description = "신규 추가된 학교 수", example = "12")
        int added,

        @Schema(description = "기존 행 갱신 수(DSRE 관리 필드만 덮어씀)", example = "1508")
        int updated,

        @Schema(description = "DSRE2에서 사라져 미사용 처리된 수(삭제 아님)", example = "3")
        int deactivated,

        @Schema(description = "매출프로그램 전용이라 건드리지 않은 행 수", example = "5")
        int keptManual,

        @Schema(description = "거래처 마스터(tbl_cust_info)에 없어 거래처명·도시·지역이 빈 채로 들어온 행 수. "
                + "0이 아니면 DSRE2 쪽 고아 매핑이므로 데이터 정합 확인 대상", example = "32")
        int missingPartnerInfo
) {
}
