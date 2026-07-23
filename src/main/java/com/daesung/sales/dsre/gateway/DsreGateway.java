package com.daesung.sales.dsre.gateway;

/**
 * DSRE2 접근 포트(어댑터 인터페이스). 로직은 DSRE2에 있고 우리는 호출/조회만.
 * 구현: JdbcDsreGateway(라이브/복제본 연결). 스택 상단은 이 인터페이스에만 의존해 연결 대상 교체 가능.
 */
public interface DsreGateway {

    /** 신청 인원 산출(DSRE2 저장함수 FUNC_REQINWON_GET 호출). 물류비 인원기준 계산에 사용. */
    Integer reqInwon(int reqCd);

    /** 출고 물류비 계산(신청 REQ 단위). DSRE2에서 자재수량×단가 집계 + 인원함수 호출. */
    OutboundLogisCost calcOutbound(int reqCd);

    /**
     * 기간 출고 물류비 집계(총계). 근거: 물류비계산2.vb OutData.
     * @param mode ALL/NORMAL(APPLY_GN='S')/ACCIDENT(APPLY_GN='A')
     * @param includeCancel true면 취소(STATE='C') 포함, false면 제외(기본)
     */
    PeriodLogisCost calcOutboundPeriod(java.time.LocalDate from, java.time.LocalDate to,
                                       LogisMode mode, boolean includeCancel);

    /**
     * 기간 회수 물류비 집계(총계, 자재금액만). 근거: 물류비계산2.vb InData.
     * @param mode ALL(전체)/NORMAL(반품 tbl_wol_dtl_b)/ACCIDENT(사고 tbl_wol_dtl)
     */
    PeriodLogisCost calcReturnPeriod(java.time.LocalDate from, java.time.LocalDate to, LogisMode mode);

    /** 매출일괄등록 대상(미처리 state='A') 조회. 신청일자 기간 필터. */
    java.util.List<BooklistImportRow> readPendingBooklist(java.time.LocalDate from, java.time.LocalDate to);

    /** write-back: 해당 (신청×분류×도서)를 처리완료(state='T')로. 중복방지. */
    void markBooklistDone(int reqCd, String lstCd, String dtlCd);
}
