package com.daesung.sales.dsre.gateway;

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
}
