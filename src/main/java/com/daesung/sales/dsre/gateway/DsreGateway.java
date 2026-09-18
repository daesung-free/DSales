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
     * 출고 물류비 <b>명세 행</b>(28p 그리드). 상품×학년×시행×신청×거래처 단위.
     * 총계({@link #calcOutboundPeriod})와 달리 화면에 뿌릴 행을 낸다 —
     * 정본 28p 데이터 항목이 처음부터 행 단위였다.
     *
     * <p>★구분·취소로 <b>걸러서 내지 않는다</b>. 행에 담아 보내고 판정은 자바에서 한다 —
     * 마감된 달은 스냅샷을 읽으므로, SQL에서 걸러 두면 판정이 두 벌이 된다.
     */
    java.util.List<LogisCostDetailRow> outboundDetail(java.time.LocalDate from, java.time.LocalDate to);

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

    /** 물류단가 단건 조회(없으면 empty). 일괄 수정 시 "입력한 항목만 반영"하려면 기존 값이 필요하다. */
    java.util.Optional<LogisCostRate> findLogisCost(int dtlCd);

    /** 물류단가 삭제(dtl_cd). 삭제 행수 반환. */
    int deleteLogisCost(int dtlCd);

    /** 회수단가(dtl_cd=0) 수정(PAPER/OMR/ETC만). 없으면 생성. */
    void updateReturnRate(int paper, int omr, int etc);

    /** 매출일괄등록(교재) 대상(미처리 state='A') 조회. 신청일자 기간 필터. */
    java.util.List<BooklistImportRow> readPendingBooklist(java.time.LocalDate from, java.time.LocalDate to);

    /**
     * 매출일괄등록(14p, 더프) 대상 조회. 근거: 레거시 {@code 매출가져오기.vb:415}.
     * 지사신청분(apply_gn='S')만. 인원 4종을 다 실어 오고 청구 판정은 호출부(Java)가 한다.
     *
     * @param onlyComplete true면 발송완료(state='D')만 — 레거시 체크박스 {@code CheckBox_OnlyComplete}
     * @param mode         처리구분 모드. <b>정가(단가) 조인에 관여</b>하므로 조회 필터가 아니라 인자다
     */
    java.util.List<DuffSalesRow> readDuffSales(java.time.LocalDate from, java.time.LocalDate to,
                                               boolean onlyComplete, DuffChargeMode mode);

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

    /** 주문 1건 조회(없으면 empty). 상태 전환 전 현재값 확인용. */
    java.util.Optional<DsreOrderRow> findOrder(int reqCd);

    /**
     * 거래명세서 발급 → 발송준비중(W) 전환. <b>상품준비중(S)일 때만</b> 바뀐다.
     *
     * <p>조건부 UPDATE인 이유: 명세서를 재출력했다고 이미 발송완료(D)된 건이 W로 되돌아가면
     * 상태가 거꾸로 간다. 조건에 안 맞으면 0을 반환하고 아무것도 바꾸지 않는다(재출력 안전).
     *
     * @return 실제로 바뀐 행 수(0 또는 1)
     */
    int markReadyToShip(int reqCd);

    /**
     * 진행상태 전환(조건부). <b>현재 상태가 {@code fromCode}일 때만</b> 바꾼다.
     * 근거: 자료요청서 3-2(가) — 발송준비중 "되돌릴 때는 수동 전환", 발송완료 "체크박스 다건 일괄".
     *
     * <p>★조건부인 이유는 {@link #markReadyToShip}과 같다. 조회한 뒤 바꾸기까지 사이에
     * DSRE2 데스크톱이 먼저 상태를 옮길 수 있다(같은 행을 두 시스템이 쓴다).
     * 무조건 UPDATE면 남이 발송완료한 건을 우리가 되돌려 놓는다.
     * 0이 오면 "그 사이 누가 바꿨다"는 뜻이므로 성공으로 보고하면 안 된다.
     *
     * @return 실제로 바뀐 행 수(0 또는 1)
     */
    int changeState(int reqCd, String fromCode, String toCode);

    /**
     * 신청 가능한 시행 목록(주문 등록 1단계). 판매중인 것만 — 종료된 시행으로는 신청할 수 없다.
     *
     * @param keyword 시행명·상품명 부분일치. null이면 전체
     */
    java.util.List<ExamRow> listExams(String keyword);

    /**
     * 시행의 과목 목록(과목신청용). {@code DISP_GN='Y'}만 —
     * 레거시 신청 화면과 같은 조건이다({@code Application_SQL.xml}).
     */
    java.util.List<SubjectRow> listSubjects(int dtlCd);

    /**
     * 신규 주문 등록. 근거: 레거시 특약점 사이트 {@code Application_SQL.xml:266~299}.
     *
     * <p>★<b>테이블 셋을 한 트랜잭션으로</b> 넣는다 —
     * {@code tbl_request_info}(주문) → {@code tbl_request_dtl}(반) → {@code tbl_request_cnt}(과목수량).
     * 중간에 실패하면 반·수량이 빠진 <b>반쪽 주문</b>이 남아, 물류가 무엇을 보낼지 알 수 없게 된다.
     *
     * @return 채번된 REQ_CD
     */
    int createOrder(NewOrder order, String actor);
}
