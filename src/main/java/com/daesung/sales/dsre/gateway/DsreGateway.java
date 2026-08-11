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

    // ── 물류단가 관리(DSRE2 tbl_logis_cost write-back) — 근거: 레거시 물류비용등록.vb ──

    /** 물류단가 전체 목록(DTL_CD 오름차순). */
    java.util.List<LogisCostRate> listLogisCosts();

    /** 물류단가 등록/수정(dtl_cd 기준 upsert). */
    void upsertLogisCost(int dtlCd, int paper, int omr, int etc, int label,
                         int basic, int trade, int packtype, String bSpare);

    /** 물류단가 삭제(dtl_cd). 삭제 행수 반환. */
    int deleteLogisCost(int dtlCd);

    /** 회수단가(dtl_cd=0) 수정(PAPER/OMR/ETC만). 없으면 생성. */
    void updateReturnRate(int paper, int omr, int etc);

    /** 매출일괄등록 대상(미처리 state='A') 조회. 신청일자 기간 필터. */
    java.util.List<BooklistImportRow> readPendingBooklist(java.time.LocalDate from, java.time.LocalDate to);

    /** write-back: 해당 (신청×분류×도서)를 처리완료(state='T')로. 중복방지. */
    void markBooklistDone(int reqCd, String lstCd, String dtlCd);

    /**
     * 학교관리 '가져오기' 원본 — 지사↔학교/학원 매핑 전량(35p).
     * 근거: DSRE2 {@code tbl_cust_ref} UNIQUE (CUST_CD, MGR_GN, MGR_CD).
     */
    java.util.List<SchoolRefRow> readSchoolRefs();

    /**
     * 주문·진행상태 조회(읽기 전용). 근거: 레거시 조회 SQL(FM_DSRE_RegStateChng.cs).
     *
     * <p>상태 전이는 DSRE2 데스크톱이 수행하고 우리는 읽기만 한다(발주처 확정 2026-08-11
     * "DSRE는 그대로 사용"). 따라서 이 게이트웨이에 상태 변경 메서드는 두지 않는다.
     *
     * @param state 진행상태 필터(null=전체). 취소분은 {@code C}로만 조회되며 지사 취소는 행이 없다.
     * @param custCode 거래처코드 필터(null=전체)
     * @param mode 구분 필터 ALL/NORMAL(APPLY_GN='S')/ACCIDENT(APPLY_GN='A')
     */
    java.util.List<DsreOrderRow> findOrders(java.time.LocalDate from, java.time.LocalDate to,
                                            OrderState state, String custCode, LogisMode mode);
}
