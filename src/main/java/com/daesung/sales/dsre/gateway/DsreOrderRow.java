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
        @Schema(description = "비고") String memo,

        @Schema(description = """
                총 신청수량 — DSRE2 `tbl_request_cnt.CNT` 합계(과목별 신청갯수).
                2026-09-16에 붙였다. 화면이 이 값이 없어 주문조회 연결을 미뤄 두고 있었다.""",
                example = "1200")
        long totalQty,

        @Schema(description = "품목건수 — 서로 다른 과목(RES_CD) 수", example = "4")
        int itemCount,

        @Schema(description = """
                **예상금액** = 단가 × 인원. 단가는 DSRE2 `tbl_product_amt` 에서
                (시행 × 과목수 구간 × 처리여부)로 정해진다 — 더프가 인원 종량제라
                과목을 몇 개 신청했느냐로 단가가 달라진다.

                <p>‼️**청구액이 아니다.** 실제 청구는 매출일괄등록이 `처리/신청/등록` 중
                무엇으로 청구할지 정해 확정한다. 이 값을 청구액으로 읽으면 정산 때 어긋난다.

                <p>‼️단가가 등록되지 않은 시행은 **null**이다(실측 커버리지 61%, 2019~2026 고르게).
                0으로 채우지 않는다 — 0원짜리 주문으로 읽히면 그게 더 나쁘다.""",
                example = "1470000")
        Long estimatedAmount
,

        @Schema(description = """
                이 주문이 만들어진 **매출번호 목록**. DSRE2가 아니라 우리 매출 원장에서 붙인다
                (서로 다른 DB라 한 쿼리로 못 잇는다).

                ★<b>출고번호와 같은 값</b>이다 — 정상출고는 별도 전표를 두지 않고
                매출번호(I-)로 흡수한다. 화면의 '출고번호·매출번호' 두 칸에 같은 값을 쓰면 된다.
                ‼️주문번호 칸이 생기기 전(2026-09-18 이전) 매출은 비어 있다. 소급하지 않았다.""")
        java.util.List<String> salesNos) {

    /** 매출번호를 붙인 사본. 게이트웨이는 null 로 두고 서비스가 채운다. */
    public DsreOrderRow withSalesNos(java.util.List<String> nos) {
        return new DsreOrderRow(reqCd, reqDate, stateCode, stateName, custCode, custName,
                custFullName, cityName, mgrCode, mgrName, productName, detailName, grade,
                inwon, classCount, procYn, teacher, tel, address, memo,
                totalQty, itemCount, estimatedAmount, nos);
    }
}
