package com.daesung.sales.sale.entity;

/**
 * 성적처리 구분(모의고사 등 인원 기준 매출). 근거: 요구사항 37p 월별매출액명세서 + DSRE PROC_YN.
 * null=비처리로 간주(집계 시 UNGRADED 버킷). 인원기준 확정(레거시 실측+재무팀 이슈#86).
 * ⚠️ DSRE 모의고사 신청(PROC_YN)에서의 자동 판정·인원 import는 후속(현재 수기 매출등록에서 지정).
 */
public enum ProcType {
    /** 성적처리(응시 후 성적 처리된 인원분). */
    GRADED,
    /** 비처리(성적 미처리분). */
    UNGRADED
}
