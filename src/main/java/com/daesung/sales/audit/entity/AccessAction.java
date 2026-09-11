package com.daesung.sales.audit.entity;

/** 기록하는 행위. 조회(GET 목록)는 넣지 않는다 — 다운로드 기록이 그 안에 묻힌다. */
public enum AccessAction {
    /** ★핵심 — 파일이 밖으로 나간 기록. 이걸 남기려고 만든 표다. */
    DOWNLOAD("다운로드"),
    LOGIN("로그인"),
    LOGOUT("로그아웃"),
    CREATE("등록"),
    UPDATE("수정"),
    DELETE("삭제");

    private final String label;

    AccessAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
