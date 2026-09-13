package com.daesung.sales.dsre.gateway;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DSRE2(MySQL) 직접 연결 구현. 저장함수/쿼리를 호출만 하고 로직은 재구현하지 않음.
 * daesung.dsre.enabled=true일 때만 활성(dsreJdbcTemplate 필요).
 */
@Component
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
@RequiredArgsConstructor
public class JdbcDsreGateway implements DsreGateway {

    private final JdbcTemplate dsreJdbcTemplate;

    @Override
    public Integer reqInwon(int reqCd) {
        // DSRE2 저장함수 호출(재구현 아님). NULL 가능 → Integer.
        return dsreJdbcTemplate.queryForObject("SELECT FUNC_REQINWON_GET(?)", Integer.class, reqCd);
    }

    private static final String MATERIAL_SQL = """
            SELECT
              COALESCE(SUM(CASE WHEN c.NAME NOT IN ('OMR','단행본','책자','라벨') THEN lc.REQCNT*cost.PAPER ELSE 0 END),0) paper_amt,
              COALESCE(SUM(CASE WHEN c.NAME='OMR' THEN lc.REQCNT*cost.OMR ELSE 0 END),0) omr_amt,
              COALESCE(SUM(CASE WHEN c.NAME IN ('단행본','책자') THEN lc.REQCNT*cost.ETC ELSE 0 END),0) etc_amt,
              COALESCE(SUM(CASE WHEN c.NAME='라벨' THEN lc.REQCNT*cost.LABEL ELSE 0 END),0) label_amt,
              COALESCE(MAX(cost.PACKTYPE),0) packtype, COALESCE(MAX(cost.BASIC),0) basic, COALESCE(MAX(cost.TRADE),0) trade
            FROM tbl_logis_cnt lc
              JOIN tbl_resource_info r ON lc.RES_CD=r.RES_CD
              JOIN tbl_materials_info m ON r.MAT_CD=m.MAT_CD
              JOIN tbl_comon_info c ON m.MAT_GN=c.CMD_CD
              JOIN tbl_request_info req ON lc.REQ_CD=req.REQ_CD
              JOIN tbl_logis_cost cost ON cost.DTL_CD=req.DTL_CD
            WHERE lc.REQ_CD=? AND lc.RES_GN='R'
            """;

    @Override
    public OutboundLogisCost calcOutbound(int reqCd) {
        // 1) 자재금액 + 단가정보(DSRE2에서 집계)
        Map<String, Object> r = dsreJdbcTemplate.queryForMap(MATERIAL_SQL, reqCd);
        long paper = num(r.get("paper_amt"));
        long omr = num(r.get("omr_amt"));
        long etc = num(r.get("etc_amt"));
        long label = num(r.get("label_amt"));
        int packtype = (int) num(r.get("packtype"));
        int basic = (int) num(r.get("basic"));
        int trade = (int) num(r.get("trade"));
        long material = paper + omr + etc + label;

        // 2) 인원(PACKTYPE별 DSRE2 함수 호출). 1·2=PACKTYPE1, 3(SET)=PACKTYPE2.
        // SQL을 조립하지 않고 완성된 상수 중에서 고른다 — 문자열 결합이 없으면 인젝션 여지 자체가 없다.
        String sql = (packtype == 3) ? INWON_PACKTYPE2_SQL : INWON_PACKTYPE1_SQL;
        Integer inwonObj = dsreJdbcTemplate.queryForObject(sql, Integer.class, reqCd);
        int inwon = (inwonObj == null) ? 0 : inwonObj;

        long labor = (long) inwon * (basic + trade);
        return new OutboundLogisCost(reqCd, paper, omr, etc, label, material,
                inwon, basic, trade, labor, material + labor);
    }

    /** PACKTYPE별 인원 산출 함수 호출. 조립 대신 완성된 상수 2개로 둔다(정적분석 SQLi 0건 기준). */
    private static final String INWON_PACKTYPE1_SQL = "SELECT FUNC_REQINWON_GET_PACKTYPE1(?)";
    private static final String INWON_PACKTYPE2_SQL = "SELECT FUNC_REQINWON_GET_PACKTYPE2(?)";

    private static long num(Object o) {
        return (o == null) ? 0L : ((Number) o).longValue();
    }

    // ── 기간 출고 물류비 집계 (물류비계산2.vb OutData) ─────────────────────────────
    // 자재금액: 기간(REQ_DATE)·구분(APPLY_GN)·취소제외(STATE) 필터로 4분류 합산.
    // mode/cancel 조각은 LogisMode enum·불리언에서만 생성(사용자 문자열 미주입).
    private static final String OUT_MATERIAL_SQL = """
            SELECT
              COALESCE(SUM(CASE WHEN c.NAME NOT IN ('OMR','단행본','책자','라벨') THEN lc.REQCNT*cost.PAPER ELSE 0 END),0) paper_amt,
              COALESCE(SUM(CASE WHEN c.NAME='OMR' THEN lc.REQCNT*cost.OMR ELSE 0 END),0) omr_amt,
              COALESCE(SUM(CASE WHEN c.NAME IN ('단행본','책자') THEN lc.REQCNT*cost.ETC ELSE 0 END),0) etc_amt,
              COALESCE(SUM(CASE WHEN c.NAME='라벨' THEN lc.REQCNT*cost.LABEL ELSE 0 END),0) label_amt
            FROM tbl_logis_cnt lc
              JOIN tbl_resource_info r ON lc.RES_CD=r.RES_CD
              JOIN tbl_materials_info m ON r.MAT_CD=m.MAT_CD
              JOIN tbl_comon_info c ON m.MAT_GN=c.CMD_CD
              JOIN tbl_request_info req ON lc.REQ_CD=req.REQ_CD
              JOIN tbl_logis_cost cost ON cost.DTL_CD=req.DTL_CD
            WHERE lc.RES_GN='R' AND req.REQ_DATE BETWEEN ? AND ?
              AND (? IS NULL OR req.APPLY_GN = ?)
              AND (? = 1 OR req.STATE != 'C')
            """;

    // 인원비: 신청(REQ) 단위로 인원 1회 산정(PACKTYPE별 함수) 후 인별 단가(BASIC+TRADE)로 합산.
    // 레거시 @vjob rownum 트릭(신청당 인원 1회)을 GROUP BY REQ_CD로 동등 구현.
    private static final String OUT_LABOR_SQL = """
            SELECT COALESCE(SUM(inwon),0) inwon_total,
                   COALESCE(SUM(inwon*(basic+trade)),0) labor,
                   COUNT(*) req_cnt
            FROM (
              SELECT req.REQ_CD,
                CASE WHEN MAX(cost.PACKTYPE)=3 THEN FUNC_REQINWON_GET_PACKTYPE2(req.REQ_CD)
                     ELSE FUNC_REQINWON_GET_PACKTYPE1(req.REQ_CD) END inwon,
                MAX(cost.BASIC) basic, MAX(cost.TRADE) trade
              FROM tbl_logis_cnt lc
                JOIN tbl_request_info req ON lc.REQ_CD=req.REQ_CD
                JOIN tbl_logis_cost cost ON cost.DTL_CD=req.DTL_CD
              WHERE lc.RES_GN='R' AND req.REQ_DATE BETWEEN ? AND ?
                AND (? IS NULL OR req.APPLY_GN = ?)
                AND (? = 1 OR req.STATE != 'C')
              GROUP BY req.REQ_CD ) t
            """;

    // ── 출고 물류비 명세 행(28p 그리드) — 물류비계산2.vb 그리드 ────────────────────
    // 정본 28p가 요구하는 컬럼(접수일자·상품·학년·시행·거래처·자재/시험지/OMR/기타·인원·작업비·합계)을
    // 상품×학년×시행×신청×거래처 단위로 낸다.
    //
    // ★인원을 이 GROUP BY 안에서 SUM 하지 않는 이유
    //   FUNC_REQINWON_GET_PACKTYPE*는 신청(REQ_CD) 단위 값이라, 자재 행마다 곱해 더하면
    //   자재 종류 수만큼 부풀려진다. 레거시가 rownum 트릭으로 "신청당 1회"를 만든 것도 같은 이유다.
    //   여기서는 신청이 GROUP BY에 들어 있으므로 MAX()로 한 번만 집는다.
    //
    // ★구분(APPLY_GN)·취소(STATE)로 <b>걸러서 내지 않는다</b> — 행에 담아 보내고 자바에서 판정한다.
    //   마감된 달은 저장해 둔 스냅샷을 읽는데, SQL에서 걸러 버리면 라이브는 SQL 조건,
    //   스냅샷은 자바 조건으로 판정이 두 벌이 되어 마감 전후로 숫자가 달라질 수 있다.
    //
    // ★거래처 <b>코드는 신청에서</b>, 이름만 거래처 마스터에서 가져온다.
    //   마스터에 없는 코드가 실재한다(복제본 실측: 7월 1,600건 중 638건 미매칭).
    //   조인 결과의 코드를 쓰면 그런 신청이 전부 코드 NULL이 되어, 거래처 축으로 묶을 때
    //   서로 다른 거래처가 한 덩어리로 뭉친다. 이름은 못 붙여도 코드로는 갈라져야 한다.
    private static final String OUT_DETAIL_SQL = """
            SELECT req.REQ_DATE, req.REQ_CD, pi.PROD_CD, pi.PROD_NM,
                   pd.GRADE, req.DTL_CD, pd.DTL_NM, req.CUST_CD, cu.CUST_NM,
              COALESCE(SUM(lc.REQCNT),0) mat_qty,
              COALESCE(SUM(CASE WHEN c.NAME NOT IN ('OMR','단행본','책자','라벨') THEN lc.REQCNT ELSE 0 END),0) paper_qty,
              COALESCE(SUM(CASE WHEN c.NAME NOT IN ('OMR','단행본','책자','라벨') THEN lc.REQCNT*cost.PAPER ELSE 0 END),0) paper_amt,
              COALESCE(SUM(CASE WHEN c.NAME='OMR' THEN lc.REQCNT ELSE 0 END),0) omr_qty,
              COALESCE(SUM(CASE WHEN c.NAME='OMR' THEN lc.REQCNT*cost.OMR ELSE 0 END),0) omr_amt,
              COALESCE(SUM(CASE WHEN c.NAME IN ('단행본','책자','라벨') THEN lc.REQCNT ELSE 0 END),0) etc_qty,
              COALESCE(SUM(CASE WHEN c.NAME IN ('단행본','책자') THEN lc.REQCNT*cost.ETC
                                WHEN c.NAME='라벨' THEN lc.REQCNT*cost.LABEL ELSE 0 END),0) etc_amt,
              MAX(CASE WHEN cost.PACKTYPE=3 THEN FUNC_REQINWON_GET_PACKTYPE2(req.REQ_CD)
                       ELSE FUNC_REQINWON_GET_PACKTYPE1(req.REQ_CD) END) inwon,
              MAX(cost.BASIC) basic, MAX(cost.TRADE) trade,
              MAX(req.APPLY_GN) apply_gn, MAX(req.STATE='C') canceled
            FROM tbl_logis_cnt lc
              JOIN tbl_resource_info r ON lc.RES_CD=r.RES_CD
              JOIN tbl_materials_info m ON r.MAT_CD=m.MAT_CD
              JOIN tbl_comon_info c ON m.MAT_GN=c.CMD_CD
              JOIN tbl_request_info req ON lc.REQ_CD=req.REQ_CD
              JOIN tbl_logis_cost cost ON cost.DTL_CD=req.DTL_CD
              JOIN tbl_product_dtl pd ON pd.DTL_CD=req.DTL_CD
              JOIN tbl_product_info pi ON pi.PROD_CD=pd.PROD_CD
              LEFT JOIN tbl_cust_info cu ON cu.CUST_CD=req.CUST_CD
            WHERE lc.RES_GN='R' AND req.REQ_DATE BETWEEN ? AND ?
            GROUP BY req.REQ_DATE, req.REQ_CD, pi.PROD_CD, pi.PROD_NM,
                     pd.GRADE, req.DTL_CD, pd.DTL_NM, req.CUST_CD, cu.CUST_NM
            ORDER BY pi.PROD_CD, pd.GRADE DESC, req.DTL_CD DESC, req.CUST_CD, req.REQ_DATE
            """;

    @Override
    public List<LogisCostDetailRow> outboundDetail(LocalDate from, LocalDate to) {
        String f = from.format(YYYYMMDD), t = to.format(YYYYMMDD);
        return dsreJdbcTemplate.query(OUT_DETAIL_SQL, (rs, i) -> {
            int inwon = rs.getInt("inwon");
            long basicAmt = (long) inwon * rs.getInt("basic");
            long tradeAmt = (long) inwon * rs.getInt("trade");
            long matAmt = rs.getLong("paper_amt") + rs.getLong("omr_amt") + rs.getLong("etc_amt");
            return new LogisCostDetailRow(
                    parseYmd(rs.getString("REQ_DATE")), rs.getInt("REQ_CD"),
                    rs.getString("PROD_CD"), rs.getString("PROD_NM"),
                    rs.getString("GRADE"), rs.getInt("DTL_CD"), rs.getString("DTL_NM"),
                    rs.getString("CUST_CD"), rs.getString("CUST_NM"),
                    rs.getLong("mat_qty"),
                    rs.getLong("paper_qty"), rs.getLong("paper_amt"),
                    rs.getLong("omr_qty"), rs.getLong("omr_amt"),
                    rs.getLong("etc_qty"), rs.getLong("etc_amt"),
                    inwon, basicAmt, tradeAmt, matAmt + basicAmt + tradeAmt,
                    rs.getString("apply_gn"), rs.getBoolean("canceled"), false);
        }, f, t);
    }

    /** DSRE2 날짜는 yyyyMMdd 문자열이다(varchar). 형식이 어긋나면 null — 행을 버리지는 않는다. */
    private static LocalDate parseYmd(String v) {
        if (v == null || v.length() < 8) {
            return null;
        }
        try {
            return LocalDate.parse(v.substring(0, 8), YYYYMMDD);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    @Override
    public PeriodLogisCost calcOutboundPeriod(LocalDate from, LocalDate to, LogisMode mode, boolean includeCancel) {
        // 필터를 SQL 조각으로 이어붙이지 않고 바인딩 값으로 넘긴다(게이트규칙: 파라미터 바인딩 전수 적용).
        //   applyGn = null 이면 구분 필터 없음(ALL), 값이 있으면 해당 APPLY_GN 만
        //   cancelFlag = 1 이면 취소 포함, 0 이면 STATE='C' 제외
        String applyGn = mode.applyGnValue();
        int cancelFlag = includeCancel ? 1 : 0;
        String f = from.format(YYYYMMDD), t = to.format(YYYYMMDD);

        Map<String, Object> mat = dsreJdbcTemplate.queryForMap(
                OUT_MATERIAL_SQL, f, t, applyGn, applyGn, cancelFlag);
        Map<String, Object> lab = dsreJdbcTemplate.queryForMap(
                OUT_LABOR_SQL, f, t, applyGn, applyGn, cancelFlag);

        return PeriodLogisCost.outbound(mode, from, to,
                num(mat.get("paper_amt")), num(mat.get("omr_amt")),
                num(mat.get("etc_amt")), num(mat.get("label_amt")),
                (int) num(lab.get("inwon_total")), num(lab.get("labor")),
                (int) num(lab.get("req_cnt")));
    }

    // ── 기간 회수 물류비 집계 (물류비계산2.vb InData) ─────────────────────────────
    // 회수분 원천: 사고=tbl_wol_dtl, 반품=tbl_wol_dtl_b. 회수 단가는 tbl_logis_cost DTL_CD=0.
    // 기간은 REG_DATE(yyyy-MM-dd)를 yyyyMMdd로 변환해 문자열 비교(레거시 동일).
    private static final String RETURN_TEMPLATE = """
            SELECT
              COALESCE(SUM(CASE WHEN c.NAME NOT IN ('OMR','단행본','책자','라벨') THEN t.CNT*cost.PAPER ELSE 0 END),0) paper_amt,
              COALESCE(SUM(CASE WHEN c.NAME='OMR' THEN t.CNT*cost.OMR ELSE 0 END),0) omr_amt,
              COALESCE(SUM(CASE WHEN c.NAME IN ('단행본','책자') THEN t.CNT*cost.ETC ELSE 0 END),0) etc_amt,
              COALESCE(SUM(CASE WHEN c.NAME='라벨' THEN t.CNT*cost.LABEL ELSE 0 END),0) label_amt
            FROM ( %s ) t
              JOIN tbl_materials_info m ON t.MAT_CD=m.MAT_CD
              JOIN tbl_comon_info c ON m.MAT_GN=c.CMD_CD
              JOIN (SELECT PAPER,OMR,ETC,LABEL FROM tbl_logis_cost WHERE DTL_CD=0 ORDER BY idx DESC LIMIT 1) cost
            WHERE t.SDATE BETWEEN ? AND ?
            """;

    private static final String WOL_ACCIDENT = "SELECT MAT_CD, CNT, REPLACE(REG_DATE,'-','') SDATE FROM tbl_wol_dtl";
    private static final String WOL_NORMAL = "SELECT MAT_CD, CNT, REPLACE(REG_DATE,'-','') SDATE FROM tbl_wol_dtl_b";

    // ── 물류단가 관리(DSRE2 tbl_logis_cost write-back) ─────────────────────────────
    // 상품명·시행명을 함께 뽑는다(정본 10.물류비용등록 목록 컬럼).
    // LEFT JOIN인 이유: dtl_cd=0(회수단가 특수행)은 시행이 없어 INNER면 행이 사라진다.
    private static final String RATE_LIST_SQL = """
            SELECT lc.DTL_CD, lc.PAPER, lc.OMR, lc.ETC, lc.LABEL, lc.BASIC, lc.TRADE,
                   lc.PACKTYPE, lc.bSpare, lc.INPUTDATE,
                   pi.PROD_NM, pd.DTL_NM
              FROM tbl_logis_cost lc
              LEFT JOIN tbl_product_dtl  pd ON pd.DTL_CD  = lc.DTL_CD
              LEFT JOIN tbl_product_info pi ON pi.PROD_CD = pd.PROD_CD
             ORDER BY lc.DTL_CD
            """;

    @Override
    public List<LogisCostRate> listLogisCosts() {
        return dsreJdbcTemplate.query(RATE_LIST_SQL,
                (rs, i) -> new LogisCostRate(
                        rs.getInt("DTL_CD"),
                        rs.getString("PROD_NM"), rs.getString("DTL_NM"),
                        rs.getInt("PAPER"), rs.getInt("OMR"), rs.getInt("ETC"),
                        rs.getInt("LABEL"), rs.getInt("BASIC"), rs.getInt("TRADE"),
                        rs.getInt("PACKTYPE"), rs.getString("bSpare"),
                        rs.getTimestamp("INPUTDATE") == null
                                ? null : rs.getTimestamp("INPUTDATE").toLocalDateTime()));
    }

    @Override
    public java.util.Optional<LogisCostRate> findLogisCost(int dtlCd) {
        // 목록을 재사용한다 — 조인·매핑이 한 곳에만 있어야 두 경로의 결과가 갈리지 않는다.
        // 단가 행은 시행 수만큼이라 전량을 훑어도 부담이 없다.
        return listLogisCosts().stream().filter(r -> r.dtlCd() == dtlCd).findFirst();
    }

    @Override
    public void upsertLogisCost(int dtlCd, int paper, int omr, int etc, int label,
                                int basic, int trade, int packtype, String bSpare) {
        // PK가 (idx, dtl_cd)라 dtl_cd 단일 ON DUPLICATE 불가 → 존재검사 후 UPDATE/INSERT(레거시 동일).
        Integer cnt = dsreJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tbl_logis_cost WHERE dtl_cd=?", Integer.class, dtlCd);
        if (cnt != null && cnt > 0) {
            dsreJdbcTemplate.update("""
                    UPDATE tbl_logis_cost SET PAPER=?,OMR=?,ETC=?,LABEL=?,BASIC=?,TRADE=?,PACKTYPE=?,bSpare=?,INPUTDATE=now()
                    WHERE dtl_cd=?
                    """, paper, omr, etc, label, basic, trade, packtype, bSpare, dtlCd);
        } else {
            dsreJdbcTemplate.update("""
                    INSERT INTO tbl_logis_cost (dtl_cd,paper,omr,etc,label,basic,trade,packtype,bSpare,inputDate)
                    VALUES (?,?,?,?,?,?,?,?,?,now())
                    """, dtlCd, paper, omr, etc, label, basic, trade, packtype, bSpare);
        }
    }

    @Override
    public int deleteLogisCost(int dtlCd) {
        return dsreJdbcTemplate.update("DELETE FROM tbl_logis_cost WHERE dtl_cd=?", dtlCd);
    }

    @Override
    public void updateReturnRate(int paper, int omr, int etc) {
        int updated = dsreJdbcTemplate.update(
                "UPDATE tbl_logis_cost SET PAPER=?,OMR=?,ETC=?,INPUTDATE=now() WHERE dtl_cd=0", paper, omr, etc);
        if (updated == 0) {
            // 회수단가 행(dtl_cd=0) 부재 시 생성(LABEL/BASIC/TRADE=0, packtype=1).
            dsreJdbcTemplate.update("""
                    INSERT INTO tbl_logis_cost (dtl_cd,paper,omr,etc,label,basic,trade,packtype,bSpare,inputDate)
                    VALUES (0,?,?,?,0,0,0,1,'Y',now())
                    """, paper, omr, etc);
        }
    }

    @Override
    public PeriodLogisCost calcReturnPeriod(LocalDate from, LocalDate to, LogisMode mode) {
        String source = switch (mode) {
            case ACCIDENT -> WOL_ACCIDENT;
            case NORMAL -> WOL_NORMAL;
            case ALL -> WOL_ACCIDENT + " UNION ALL " + WOL_NORMAL;
        };
        Map<String, Object> r = dsreJdbcTemplate.queryForMap(
                String.format(RETURN_TEMPLATE, source), from.format(YYYYMMDD), to.format(YYYYMMDD));
        return PeriodLogisCost.ret(mode, from, to,
                num(r.get("paper_amt")), num(r.get("omr_amt")),
                num(r.get("etc_amt")), num(r.get("label_amt")));
    }

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private static final String BOOKLIST_SQL = """
            SELECT BLC.REQ_CD reqcd,
              (SELECT machul_cd FROM tbl_cust_info CUI WHERE CUI.CUST_CD=BLF.cust_cd) cust_cd,
              (SELECT cust_fnm FROM tbl_cust_info CUI WHERE CUI.CUST_CD=BLF.cust_cd) cust_nm,
              BLC.LST_CD lst_cd, BLC.DTL_CD dtl_cd, max(BLD.DTL_NM) book_nm, max(BLC.PRICE) price,
              IFNULL(max(CASE WHEN BLC.req_gn='M' THEN BLC.dissusu END),0) sale_rate, IFNULL(sum(CASE WHEN BLC.req_gn='M' THEN BLC.reqcnt END),0) sale_qty,
              IFNULL(max(CASE WHEN BLC.req_gn='J' THEN BLC.dissusu END),0) gift_rate, IFNULL(sum(CASE WHEN BLC.req_gn='J' THEN BLC.reqcnt END),0) gift_qty,
              IFNULL(max(CASE WHEN BLC.req_gn='B' THEN BLC.dissusu END),0) free_rate, IFNULL(sum(CASE WHEN BLC.req_gn='B' THEN BLC.reqcnt END),0) free_qty,
              max(BLF.memo) memo
            FROM tbf_booklist_cnt BLC
              LEFT JOIN tbf_booklist_ref BLF ON BLF.req_cd=BLC.req_cd
              LEFT JOIN tbl_booklist_dtl BLD ON BLC.lst_cd=BLD.lst_cd AND BLC.dtl_cd=BLD.dtl_cd
            WHERE BLC.state='A' AND BLF.reqdt BETWEEN ? AND ?
            GROUP BY BLC.REQ_CD, BLF.cust_cd, BLC.lst_cd, BLC.dtl_cd
            HAVING sum(BLC.reqcnt) > 0
            ORDER BY BLC.REQ_CD, BLC.lst_cd, BLC.dtl_cd
            """;

    @Override
    public List<BooklistImportRow> readPendingBooklist(LocalDate from, LocalDate to) {
        return dsreJdbcTemplate.query(BOOKLIST_SQL,
                (rs, i) -> new BooklistImportRow(
                        rs.getInt("reqcd"), rs.getString("cust_cd"), rs.getString("cust_nm"),
                        rs.getString("lst_cd"), rs.getString("dtl_cd"), rs.getString("book_nm"),
                        rs.getInt("price"),
                        rs.getInt("sale_rate"), rs.getInt("sale_qty"),
                        rs.getInt("gift_rate"), rs.getInt("gift_qty"),
                        rs.getInt("free_rate"), rs.getInt("free_qty"),
                        rs.getString("memo")),
                from.format(YYYYMMDD), to.format(YYYYMMDD));
    }

    @Override
    public void markBooklistDone(int reqCd, String lstCd, String dtlCd) {
        // 해당 (신청×분류×도서)의 M/J/B 전 행을 처리완료(state='T')로. 중복방지.
        dsreJdbcTemplate.update(
                "UPDATE tbf_booklist_cnt SET state='T' WHERE req_cd=? AND lst_cd=? AND dtl_cd=?",
                reqCd, lstCd, dtlCd);
    }

    // ── 매출일괄등록(14p, 더프) ───────────────────────────────────────────────────
    // 근거: 레거시 매출가져오기.vb:415. 지사신청분(apply_gn='S')만.
    //
    // ★레거시와 다르게 한 곳: 단가·청구인원·총금액을 SQL에서 계산하지 않는다.
    //   레거시는 화면 콤보값(처리구분)을 SQL 문자열에 끼워 넣어 CASE를 만든다
    //   ({IIf(ComboBox_처리구분.Text = "처리", True, False)}). 조건마다 다른 쿼리가 나가고,
    //   미리보기와 실제 등록이 서로 다른 문자열을 쓰게 된다.
    //   여기서는 인원 4종을 다 실어 보내고 판정은 Java 한 곳에서 한다.
    //
    // ★단 정가 조인만은 SQL에 남는다 — tbl_product_amt가 처리/비처리(proc_gn)별로
    //   단가를 따로 갖고 있어 조인 시점에 어느 쪽을 볼지 정해야 한다.
    //   문자열을 붙이는 대신 바인딩 파라미터 하나를 CASE로 받는다.
    //
    // 그룹 축은 레거시 그대로다(거래처·학교·학년·분류·과목·처리구분·청구구분·단가).
    // 레거시 주석 원문: "신청과목수로 하면 인문/자연 때문에 6/5로 나뉘는 경우가 생겨 단가로 그룹".
    private static final String DUFF_SQL = """
            SELECT
              MAX(처리순번) 처리순번, MAX(신청일자) 신청일자, MAX(매출코드) 매출코드,
              거래처코드, MAX(도시명) 도시명, MAX(거래처명) 거래처명, MAX(거래처풀네임) 거래처풀네임,
              학교코드, MAX(학교명) 학교명, 학년,
              분류코드, MAX(분류명) 분류명, 과목코드, MAX(과목명) 과목명,
              처리구분, 청구구분, MAX(신청과목수) 신청과목수,
              SUM(신청인원) 신청인원, SUM(처리인원) 처리인원,
              SUM(비처리인원) 비처리인원, SUM(등록인원) 등록인원,
              MAX(정가) 정가, MAX(공급률) 공급률, MAX(할인액) 할인액
            FROM (
              SELECT
                mInfo.req_cd 처리순번, mInfo.req_date 신청일자,
                cInfo.machul_cd 매출코드, mInfo.cust_cd 거래처코드,
                cInfo.city_nm 도시명, cInfo.cust_nm 거래처명, cInfo.cust_fnm 거래처풀네임,
                mInfo.mgr_cd 학교코드, schInfo.sch_nm 학교명, pDtl.grade 학년,
                pInfo.prod_cd 분류코드, pInfo.prod_nm 분류명,
                mInfo.dtl_cd 과목코드, pDtl.dtl_nm 과목명,
                sCnt.cnt 신청과목수,
                Func_reqinwon_get(mInfo.req_cd) 신청인원,
                IF(rRtnSum.pSum IS NULL, IFNULL(rRtn.pSum,0), rRtnSum.pSum) 처리인원,
                Func_reqinwon_get(mInfo.req_cd) + IFNULL(rCancel.cancelCnt,0)
                  - IF(rRtnSum.pSum IS NULL, IFNULL(rRtn.pSum,0), rRtnSum.pSum) 비처리인원,
                IFNULL(uCnt.cnt,0) 등록인원,
                amt.amt 정가, mInfo.proc_yn2 처리구분,
                sRef.charge_gn 청구구분, sRef.amtsusu 공급률, sRef.dissusu 할인액
              FROM (
                SELECT req_cd, dtl_cd, cust_cd, mgr_cd, req_date, proc_yn2
                FROM tbl_request_info
                WHERE req_date BETWEEN ? AND ?
                  AND apply_gn='S'
                  AND (? = 0 OR state='D')
              ) mInfo
              LEFT JOIN (
                SELECT req_cd, MAX(cnt) cnt FROM (
                  SELECT req_cd, seq, COUNT(res_cd) cnt FROM tbl_request_cnt
                  WHERE cnt > 0 GROUP BY req_cd, seq
                ) T GROUP BY req_cd
              ) sCnt ON mInfo.req_cd = sCnt.req_cd
              LEFT JOIN tbl_mgrcd_cnt uCnt
                ON mInfo.dtl_cd = uCnt.dtl_cd AND mInfo.mgr_cd = uCnt.mgr_cd
              LEFT JOIN tbl_product_amt amt
                ON mInfo.dtl_cd = amt.dtl_cd
               AND amt.proc_gn = CASE ?
                     WHEN 'Y' THEN 'Y'
                     WHEN 'N' THEN 'N'
                     ELSE IF(mInfo.proc_yn2='N','N','Y')
                   END
               AND IF(sCnt.cnt IS NULL, 5, sCnt.cnt) >= amt.substcnt
               AND IF(sCnt.cnt IS NULL, 5, sCnt.cnt) <= amt.subedcnt
              LEFT JOIN tbl_product_dtl  pDtl  ON mInfo.dtl_cd  = pDtl.dtl_cd
              LEFT JOIN tbl_product_info pInfo ON pDtl.prod_cd  = pInfo.prod_cd
              LEFT JOIN tbl_school_ref   sRef  ON mInfo.mgr_cd  = sRef.mgr_cd
                                              AND pInfo.prod_cd = sRef.prod_cd
              LEFT JOIN (SELECT req_cd, SUM(IFNULL(proCnt,0)) pSum
                           FROM tbl_request_rtn GROUP BY req_cd) rRtn
                ON mInfo.req_cd = rRtn.req_cd
              LEFT JOIN (SELECT req_cd, SUM(IFNULL(proc_y_Cnt,0)) pSum
                           FROM tbl_request_rtnSum GROUP BY req_cd) rRtnSum
                ON mInfo.req_cd = rRtnSum.req_cd
              LEFT JOIN (SELECT req_cd, SUM(IFNULL(cancelCnt,0)) cancelCnt
                           FROM tbl_request_cancel GROUP BY req_cd) rCancel
                ON mInfo.req_cd = rCancel.req_cd
              LEFT JOIN (
                SELECT mgr_cd, sch_nm FROM tbl_school_info
                UNION
                SELECT mgr_cd, hak_nm FROM tbl_hakwon_info
              ) schInfo ON mInfo.mgr_cd = schInfo.mgr_cd
              LEFT JOIN tbl_cust_info cInfo ON mInfo.cust_cd = cInfo.cust_cd
            ) t
            GROUP BY 거래처코드, 학교코드, 학년, 분류코드, 과목코드, 처리구분, 청구구분,
                     IF(할인액 > 0, 정가 - 할인액, 정가 * 공급률 / 100)
            ORDER BY 처리순번, 거래처코드, 학교코드, 과목코드
            """;

    @Override
    public List<DuffSalesRow> readDuffSales(LocalDate from, LocalDate to, boolean onlyComplete,
                                            DuffChargeMode mode) {
        return dsreJdbcTemplate.query(DUFF_SQL,
                (rs, i) -> new DuffSalesRow(
                        rs.getInt("처리순번"),
                        rs.getString("신청일자") == null ? null
                                : LocalDate.parse(rs.getString("신청일자"), YYYYMMDD),
                        rs.getString("매출코드"), rs.getString("거래처코드"),
                        rs.getString("거래처명"), rs.getString("거래처풀네임"), rs.getString("도시명"),
                        rs.getString("학교코드"), rs.getString("학교명"), rs.getString("학년"),
                        rs.getString("분류코드"), rs.getString("분류명"),
                        rs.getString("과목코드"), rs.getString("과목명"),
                        rs.getInt("신청과목수"), rs.getString("처리구분"), rs.getString("청구구분"),
                        rs.getInt("신청인원"), rs.getInt("처리인원"),
                        rs.getInt("비처리인원"), rs.getInt("등록인원"),
                        rs.getInt("정가"), rs.getInt("공급률"), rs.getInt("할인액"),
                        null),
                from.format(YYYYMMDD), to.format(YYYYMMDD),
                onlyComplete ? 1 : 0,
                // 단가구분 — 이건 Java로 뺄 수 없다. tbl_product_amt가 처리/비처리별로
                // 단가를 따로 갖고 있어 조인 시점에 정해져야 한다. 값은 바인딩으로 넘긴다.
                mode.rateCode());
    }

    /**
     * 학교관리 가져오기 원본. tbl_cust_ref(지사↔학교/학원)를 기준으로 거래처·학교 정보를 붙인다.
     * 학교(MGR_GN='S')는 tbl_school_info, 학원('A')은 tbl_hakwon_info에서 이름을 가져온다.
     * 도시명은 tbl_city_info PK가 (GROUP_CD, CITY_CD)라 CITY_CD 단독 조인 시 행이 불어난다
     * → 상관 서브쿼리 LIMIT 1로 고정(행 증식 방지).
     *
     * <p><b>거래처·학교 마스터는 LEFT JOIN이다(INNER 아님).</b> 실데이터 검증에서 tbl_cust_ref 76건 중
     * 32건이 tbl_cust_info에 없는 고아 매핑이었다. INNER로 조이면 매핑의 42%가 소리 없이 사라진다
     * → 매핑은 살리고 이름만 비운 뒤, 불완전 건수를 동기화 결과에 노출한다.
     */
    @Override
    public List<SchoolRefRow> readSchoolRefs() {
        return dsreJdbcTemplate.query("""
                SELECT r.CUST_CD                     cust_cd,
                       r.MGR_CD                      mgr_cd,
                       r.MGR_GN                      mgr_gn,
                       c.CUST_FNM                    cust_nm,
                       c.CITY_NM                     region,
                       (SELECT ci.CITY_NM FROM tbl_city_info ci
                         WHERE ci.CITY_CD = c.CITY_CD LIMIT 1) city,
                       COALESCE(s.SCH_NM, h.HAK_NM)  sch_nm
                  FROM tbl_cust_ref r
                  LEFT JOIN tbl_cust_info c   ON c.CUST_CD = r.CUST_CD
                  LEFT JOIN tbl_school_info s ON r.MGR_GN = 'S' AND s.MGR_CD = r.MGR_CD
                  LEFT JOIN tbl_hakwon_info h ON r.MGR_GN = 'A' AND h.MGR_CD = r.MGR_CD
                 ORDER BY r.CUST_CD, r.MGR_CD
                """,
                (rs, i) -> new SchoolRefRow(
                        trim(rs.getString("cust_cd")),
                        trim(rs.getString("mgr_cd")),
                        !"A".equalsIgnoreCase(trim(rs.getString("mgr_gn"))),
                        rs.getString("cust_nm"),
                        rs.getString("city"),
                        rs.getString("region"),
                        rs.getString("sch_nm")));
    }

    // ── 주문·진행상태 조회 (읽기 전용) — 근거: FM_DSRE_RegStateChng.cs 조회 SQL ──────────
    // 상태 전이는 DSRE2 데스크톱 소관이라 여기엔 조회만 둔다(발주처 확정 2026-08-11).
    // 레거시 화면은 'AND STATE NOT IN (W,D,C) AND APPLY_GN=S'로 대상을 좁히지만,
    // 그건 '상태를 바꿀 수 있는 건'만 뽑는 그 화면의 사정이라 일반 조회에는 옮기지 않는다.
    // 필터는 전부 바인딩 값으로 넘긴다(게이트규칙: SQL 조각 조립 금지).
    private static final String ORDER_SQL = """
            SELECT req.REQ_CD                                   req_cd,
                   req.REQ_DATE                                 req_date,
                   req.STATE                                    state,
                   req.CUST_CD                                  cust_cd,
                   cust.CUST_NM                                 cust_nm,
                   cust.CUST_FNM                                cust_fnm,
                   city.CITY_NM                                 city_nm,
                   req.MGR_CD                                   mgr_cd,
                   COALESCE(sch.SCH_NM, hak.HAK_NM)             mgr_nm,
                   prod.PROD_NM                                 prod_nm,
                   dtl.DTL_NM                                   dtl_nm,
                   dtl.GRADE                                    grade,
                   FUNC_REQINWON_GET(req.REQ_CD)                inwon,
                   cls.CLS_CNT                                  cls_cnt,
                   req.PROC_YN                                  proc_yn,
                   req.TEACHER                                  teacher,
                   req.TEL                                      tel,
                   req.ADDRESS                                  address,
                   req.BIGO                                     bigo
              FROM tbl_request_info req
              LEFT JOIN tbl_product_dtl  dtl  ON dtl.DTL_CD  = req.DTL_CD
              LEFT JOIN tbl_product_info prod ON prod.PROD_CD = dtl.PROD_CD
              LEFT JOIN tbl_cust_info    cust ON cust.CUST_CD = req.CUST_CD
              LEFT JOIN tbl_city_info    city ON city.CITY_CD = cust.CITY_CD
              LEFT JOIN tbl_school_info  sch  ON sch.MGR_CD  = req.MGR_CD
              LEFT JOIN tbl_hakwon_info  hak  ON hak.MGR_CD  = req.MGR_CD
              LEFT JOIN (SELECT REQ_CD, COUNT(CLS_NM) CLS_CNT
                           FROM tbl_request_dtl GROUP BY REQ_CD) cls ON cls.REQ_CD = req.REQ_CD
            """;

    // 기간·필터 조회 / 단건 조회. 앞부분(ORDER_SQL)을 공유해 컬럼 구성이 갈리지 않게 한다.
    private static final String ORDER_LIST_SQL = ORDER_SQL + """
             WHERE req.REQ_DATE BETWEEN ? AND ?
               AND (? IS NULL OR req.STATE = ?)
               AND (? IS NULL OR req.CUST_CD = ?)
               AND (? IS NULL OR req.APPLY_GN = ?)
             ORDER BY req.REQ_DATE DESC, req.REQ_CD DESC
            """;

    private static final String ORDER_BY_ID_SQL = ORDER_SQL + " WHERE req.REQ_CD = ?";

    private static final org.springframework.jdbc.core.RowMapper<DsreOrderRow> ORDER_MAPPER = (rs, i) -> {
        String code = trim(rs.getString("state"));
        return new DsreOrderRow(
                rs.getInt("req_cd"),
                trim(rs.getString("req_date")),
                code,
                OrderState.labelOf(code),
                trim(rs.getString("cust_cd")),
                rs.getString("cust_nm"),
                rs.getString("cust_fnm"),
                rs.getString("city_nm"),
                trim(rs.getString("mgr_cd")),
                rs.getString("mgr_nm"),
                rs.getString("prod_nm"),
                rs.getString("dtl_nm"),
                trim(rs.getString("grade")),
                intOrNull(rs.getObject("inwon")),
                intOrNull(rs.getObject("cls_cnt")),
                procLabel(trim(rs.getString("proc_yn"))),
                rs.getString("teacher"),
                rs.getString("tel"),
                rs.getString("address"),
                rs.getString("bigo"));
    };

    @Override
    public List<DsreOrderRow> findOrders(LocalDate from, LocalDate to,
                                         OrderState state, String custCode, LogisMode mode) {
        String stateCode = (state == null) ? null : state.code();
        String cust = (custCode == null || custCode.isBlank()) ? null : custCode.trim();
        String applyGn = (mode == null) ? null : mode.applyGnValue();

        return dsreJdbcTemplate.query(ORDER_LIST_SQL, ORDER_MAPPER,
                from.format(YYYYMMDD), to.format(YYYYMMDD),
                stateCode, stateCode, cust, cust, applyGn, applyGn);
    }

    @Override
    public java.util.Optional<DsreOrderRow> findOrder(int reqCd) {
        // 기간 조회와 같은 SQL을 쓰되 REQ_CD로만 좁힌다(컬럼 구성이 갈리지 않게).
        List<DsreOrderRow> rows = dsreJdbcTemplate.query(ORDER_BY_ID_SQL, ORDER_MAPPER, reqCd);
        return rows.stream().findFirst();
    }

    // 발송준비중 전환. STATE='S'(상품준비중)일 때만 바꾼다 — 재출력해도 D를 되돌리지 않는다.
    private static final String READY_TO_SHIP_SQL =
            "UPDATE tbl_request_info SET STATE='W' WHERE REQ_CD=? AND STATE='S'";

    @Override
    public int markReadyToShip(int reqCd) {
        return dsreJdbcTemplate.update(READY_TO_SHIP_SQL, reqCd);
    }

    // 진행상태 전환. 현재 상태가 일치할 때만 — 조회와 UPDATE 사이에 DSRE2 데스크톱이 먼저 옮길 수 있다.
    // ★SQL은 완성된 상수 하나다(조각 결합 금지 — 정적분석 게이트규칙). 상태값도 바인딩으로 넘긴다.
    private static final String CHANGE_STATE_SQL =
            "UPDATE tbl_request_info SET STATE=? WHERE REQ_CD=? AND STATE=?";

    @Override
    public int changeState(int reqCd, String fromCode, String toCode) {
        return dsreJdbcTemplate.update(CHANGE_STATE_SQL, toCode, reqCd, fromCode);
    }

    /**
     * 집계·저장함수 결과를 Integer로. MySQL이 COUNT()·FUNC_REQINWON_GET()을 BIGINT로 돌려줘
     * Integer 직접 캐스팅은 ClassCastException이 난다(실제로 발생).
     */
    private static Integer intOrNull(Object v) {
        return (v instanceof Number n) ? n.intValue() : null;
    }

    /** 성적처리 여부 표기(레거시 조회 SQL의 CASE와 동일). */
    private static String procLabel(String procYn) {
        if ("Y".equalsIgnoreCase(procYn)) {
            return "처리";
        }
        return "N".equalsIgnoreCase(procYn) ? "비처리" : "";
    }

    /** DSRE2 코드 컬럼이 char(5) 고정폭이라 뒤 공백이 붙는다 — 매칭키로 쓰기 전 제거. */
    private static String trim(String s) {
        return (s == null) ? null : s.trim();
    }
}
