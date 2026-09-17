package com.daesung.sales.dsre.gateway;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 물류 작업비 명세 한 행(28p). 근거: 레거시 물류비계산2.vb 그리드 +
 * 정본 28p 주요 데이터 항목("접수일자, 상품명, 학년, 시행코드/명, 거래처코드/명,
 * 자재합, 시험지합/금액, OMR합/금액, 기타비/금액, 인원, 기본작업비, 출고비, 금액합계").
 *
 * <p>★<b>가장 잘게</b> 낸다 — 상품×학년×시행×신청×거래처. 레거시는 화면 체크박스에 따라
 * 마지막 축을 신청({@code REQ_CD})으로도, 거래처({@code CUST_CD})로도 묶는데,
 * 잘게 한 번 받아 두면 어느 쪽이든 접어서 만들 수 있다.
 * 모드마다 쿼리를 따로 두면 물류비 계산식이 두 벌로 갈라진다.
 *
 * <p>⚠️인원은 <b>신청 단위로 1회</b> 산정된다(레거시 rownum 트릭 = 신청당 한 번).
 * 그래서 이 행이 신청보다 잘게 쪼개지면 인원이 중복 계상된다 — 신청이 최소 단위인 이유다.
 */
public record LogisCostDetailRow(
        @Schema(description = "접수일자") LocalDate reqDate,
        @Schema(description = "신청번호(REQ_CD)") int reqCd,
        @Schema(description = "상품코드") String productCode,
        @Schema(description = "상품명") String productName,
        @Schema(description = "학년") String grade,
        @Schema(description = "시행코드(DTL_CD)") int dtlCd,
        @Schema(description = "시행명") String detailName,
        @Schema(description = "거래처코드") String partnerCode,
        @Schema(description = "거래처명 — **지사 풀네임**(CUST_FNM). 상호(CUST_NM)가 아니다 — 레거시가 풀네임을 쓴다") String partnerName,
        @Schema(description = """
                지역 — 거래처의 **관할명**(DSRE `tbl_cust_info.CITY_NM`).
                레거시 물류비계산2.vb 가 `특약지역명`으로 부르던 값이다.
                ⚠️일부 법인은 여기에 브랜드·그룹명이 들어 있다(DSRE 원본 그대로, 우리가 보정하지 않는다).""")
        String region,

        @Schema(description = "도시명 — `tbl_city_info.CITY_NM`(거래처의 CITY_CD 기준). 레거시 `도시지역명`.")
        String cityName,

        @Schema(description = """
                학교·학원명 — `tbl_school_info` ∪ `tbl_hakwon_info` 를 MGR_CD로 찾은 값.
                ‼️못 찾으면 null이다(레거시는 내부 조인이라 행이 사라졌는데, 우리는 행을 남긴다).""")
        String schoolName,

        @Schema(description = "자재 합(수량)") long materialQty,
        @Schema(description = "시험지 합(수량)") long paperQty,
        @Schema(description = "시험지 금액") long paperAmount,
        @Schema(description = "OMR 합(수량)") long omrQty,
        @Schema(description = "OMR 금액") long omrAmount,
        @Schema(description = "기타(단행본·책자·라벨) 합(수량)") long etcQty,
        @Schema(description = "기타 금액") long etcAmount,
        @Schema(description = "인원(신청 단위 1회 산정)") int inwon,
        @Schema(description = "기본작업비(인원×BASIC)") long basicAmount,
        @Schema(description = "출고비(인원×TRADE)") long tradeAmount,
        @Schema(description = "금액합계(자재금액+기본작업비+출고비)") long totalAmount,
        @Schema(description = "구분(APPLY_GN) S=일반/A=사고") String applyGn,
        @Schema(description = "발송 후 취소 여부(STATE='C')") boolean canceled,
        @Schema(description = "수기 등록 행인가(28p 에디팅). 자동계산분은 false") boolean manual
) {
    /**
     * 이 행이 요청한 구분·취소조건에 해당하는가.
     *
     * <p>★필터를 SQL이 아니라 여기서 하는 이유: <b>마감된 달은 저장해 둔 스냅샷을 읽는다</b>.
     * SQL에서 걸러 버리면 라이브는 SQL 조건, 스냅샷은 자바 조건으로 <b>판정이 두 벌</b>이 되어
     * 같은 달인데 마감 전후로 숫자가 달라질 수 있다. 한 곳에서만 판정한다.
     */
    public boolean matches(LogisMode mode, boolean includeCancel) {
        if (!includeCancel && canceled) {
            return false;
        }
        String want = mode.applyGnValue();
        return want == null || want.equals(applyGn);
    }
}
