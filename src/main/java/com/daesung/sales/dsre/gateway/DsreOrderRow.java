package com.daesung.sales.dsre.gateway;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DSRE2 주문 1건(읽기 전용). 레거시 조회 SQL(FM_DSRE_RegStateChng.cs)의 컬럼 구성을 따랐다.
 * 인원은 DSRE2 저장함수 {@code FUNC_REQINWON_GET}가 산출한다.
 */
@Schema(name = "DsreOrderRow", description = "DSRE2 주문·진행상태 (읽기 전용)")
public record DsreOrderRow(

        @Schema(description = "신청번호(REQ_CD)", example = "78331") int reqCd,
        @Schema(description = "신청일(yyyyMMdd)", example = "20260716") String reqDate,
        @Schema(description = "진행상태 코드(DSRE2 원본)", example = "D") String stateCode,
        @Schema(description = "진행상태명", example = "발송완료") String stateName,

        @Schema(description = "거래처코드", example = "10003") String custCode,
        @Schema(description = "거래처명(상호)", example = "강남 하람도서") String custName,
        @Schema(description = "거래처 풀네임") String custFullName,
        @Schema(description = "도시/관할명", example = "서울") String cityName,

        @Schema(description = "학교/학원 코드(MGR_CD)", example = "10001") String mgrCode,
        @Schema(description = "학교/학원명", example = "국악고") String mgrName,

        @Schema(description = "상품명", example = "더 프리미엄") String productName,
        @Schema(description = "시행명(상세)", example = "7월 16일(국어)") String detailName,
        @Schema(description = "학년", example = "3") String grade,

        @Schema(description = "인원(FUNC_REQINWON_GET)", example = "103") Integer inwon,
        @Schema(description = "반 수", example = "4") Integer classCount,
        @Schema(description = "성적처리 여부(처리/비처리)", example = "처리") String procYn,

        @Schema(description = "담당 선생님") String teacher,
        @Schema(description = "연락처") String tel,
        @Schema(description = "주소") String address,
        @Schema(description = "비고") String memo
) {
}
