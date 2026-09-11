package com.daesung.sales.audit.entity;

/** 기록하는 행위. 조회(GET 목록)는 넣지 않는다 — 다운로드 기록이 그 안에 묻힌다. */
public enum AccessAction {
    /** ★핵심 — 파일이 밖으로 나간 기록. 이걸 남기려고 만든 표다. */
    DOWNLOAD("다운로드"),
    LOGIN("로그인"),
    LOGOUT("로그아웃"),
    CREATE("등록"),
    UPDATE("수정"),
    DELETE("삭제"),

    /**
     * 권한 부족으로 막힌 시도(403).
     *
     * <p>★조회(GET)여도 남긴다 — 일반 조회는 소음이라 안 남기지만, <b>막힌 조회는 소음이 아니다</b>.
     * 권한 없는 사람이 남의 다운로드 기록을 열어 보려 한 것 자체가 이 표에서 가장 봐야 할 기록이다.
     * ‼️예전엔 Spring Security가 필터 앞단에서 끊어 버려 403이 한 줄도 안 남았다
     * (409·401은 남는데 403만 빠져 있었다 — 2026-09-11 점검에서 발견).
     */
    DENIED("권한거부");

    private final String label;

    AccessAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
