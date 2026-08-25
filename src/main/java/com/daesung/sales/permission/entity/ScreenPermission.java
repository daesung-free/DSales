package com.daesung.sales.permission.entity;

/**
 * 화면 권한 수준. 발주처 매트릭스의 ○/◐/– 세 단계를 그대로 옮긴 것이다.
 *
 * <p>원문(드라이브 「사용자권한_구조_설계_예시」):
 * "○(조회·등록·수정) / ◐(조회만) / –(비노출) 3단계".
 */
public enum ScreenPermission {

    /** – 비노출. 조회도 안 된다. */
    NONE("–", "비노출"),

    /** ◐ 조회만. GET은 되고 쓰기는 막힌다. */
    READ("◐", "조회만"),

    /** ○ 조회·등록·수정. */
    WRITE("○", "조회·등록·수정");

    private final String mark;
    private final String label;

    ScreenPermission(String mark, String label) {
        this.mark = mark;
        this.label = label;
    }

    public String mark() {
        return mark;
    }

    public String label() {
        return label;
    }

    /** 이 권한으로 해당 요청이 되는가. 쓰기 요청(POST/PUT/DELETE/PATCH)은 WRITE만 통과. */
    public boolean allows(boolean writeRequest) {
        return writeRequest ? this == WRITE : this != NONE;
    }
}
