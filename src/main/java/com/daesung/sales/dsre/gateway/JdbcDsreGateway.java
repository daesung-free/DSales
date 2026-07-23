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
        String fn = (packtype == 3) ? "FUNC_REQINWON_GET_PACKTYPE2" : "FUNC_REQINWON_GET_PACKTYPE1";
        Integer inwonObj = dsreJdbcTemplate.queryForObject("SELECT " + fn + "(?)", Integer.class, reqCd);
        int inwon = (inwonObj == null) ? 0 : inwonObj;

        long labor = (long) inwon * (basic + trade);
        return new OutboundLogisCost(reqCd, paper, omr, etc, label, material,
                inwon, basic, trade, labor, material + labor);
    }

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
            """;

    @Override
    public PeriodLogisCost calcOutboundPeriod(LocalDate from, LocalDate to, LogisMode mode, boolean includeCancel) {
        String applyGn = mode.applyGnClause();
        String cancel = includeCancel ? " " : " AND req.STATE != 'C' ";
        String f = from.format(YYYYMMDD), t = to.format(YYYYMMDD);

        Map<String, Object> mat = dsreJdbcTemplate.queryForMap(OUT_MATERIAL_SQL + applyGn + cancel, f, t);
        String laborSql = OUT_LABOR_SQL + applyGn + cancel + " GROUP BY req.REQ_CD ) t";
        Map<String, Object> lab = dsreJdbcTemplate.queryForMap(laborSql, f, t);

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
    private static final String RATE_LIST_SQL = """
            SELECT DTL_CD, PAPER, OMR, ETC, LABEL, BASIC, TRADE, PACKTYPE, bSpare
            FROM tbl_logis_cost ORDER BY DTL_CD
            """;

    @Override
    public List<LogisCostRate> listLogisCosts() {
        return dsreJdbcTemplate.query(RATE_LIST_SQL,
                (rs, i) -> new LogisCostRate(
                        rs.getInt("DTL_CD"), rs.getInt("PAPER"), rs.getInt("OMR"), rs.getInt("ETC"),
                        rs.getInt("LABEL"), rs.getInt("BASIC"), rs.getInt("TRADE"),
                        rs.getInt("PACKTYPE"), rs.getString("bSpare")));
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
              IFNULL(max(CASE WHEN req_gn='M' THEN dissusu END),0) sale_rate, IFNULL(sum(CASE WHEN req_gn='M' THEN reqcnt END),0) sale_qty,
              IFNULL(max(CASE WHEN req_gn='J' THEN dissusu END),0) gift_rate, IFNULL(sum(CASE WHEN req_gn='J' THEN reqcnt END),0) gift_qty,
              IFNULL(max(CASE WHEN req_gn='B' THEN dissusu END),0) free_rate, IFNULL(sum(CASE WHEN req_gn='B' THEN reqcnt END),0) free_qty,
              max(BLF.memo) memo
            FROM tbf_booklist_cnt BLC
              LEFT JOIN tbf_booklist_ref BLF ON BLF.req_cd=BLC.req_cd
              LEFT JOIN tbl_booklist_dtl BLD ON BLC.lst_cd=BLD.lst_cd AND BLC.dtl_cd=BLD.dtl_cd
            WHERE BLC.state='A' AND BLF.reqdt BETWEEN ? AND ?
            GROUP BY BLC.REQ_CD, BLF.cust_cd, BLC.lst_cd, BLC.dtl_cd
            HAVING sum(reqcnt) > 0
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
}
