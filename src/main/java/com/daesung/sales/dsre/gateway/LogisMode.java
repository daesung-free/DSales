package com.daesung.sales.dsre.gateway;

/**
 * 물류비 집계 구분. 근거: 레거시 물류비계산2.vb 라디오(출고전체/일반/사고, 회수전체/일반/사고).
 * <ul>
 *   <li>출고: ALL=필터없음, NORMAL={@code APPLY_GN='S'}(지사신청), ACCIDENT={@code APPLY_GN='A'}(사고처리신청)</li>
 *   <li>회수: ALL={@code tbl_wol_dtl}∪{@code tbl_wol_dtl_b}, NORMAL={@code tbl_wol_dtl_b}(반품), ACCIDENT={@code tbl_wol_dtl}(사고)</li>
 * </ul>
 * SQL 인젝션 방지 — 사용자 문자열을 직접 쿼리에 넣지 않고 이 enum으로만 분기한다.
 */
public enum LogisMode {
    ALL, NORMAL, ACCIDENT;

    /** 출고 집계 시 APPLY_GN 필터 조각. ALL은 필터 없음. */
    public String applyGnClause() {
        return switch (this) {
            case NORMAL -> " AND req.APPLY_GN = 'S' ";
            case ACCIDENT -> " AND req.APPLY_GN = 'A' ";
            case ALL -> " ";
        };
    }
}
