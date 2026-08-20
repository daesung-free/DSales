package com.daesung.sales.receivable.entity;

/**
 * <b>입금구분</b> — 돈이 어떤 형태로 들어왔는가. 근거: 레거시 수금등록.vb:62
 * {@code collType as '입금구분'} + 라디오 4종(현금/어음/선수금/대체).
 *
 * <p>★이름 주의: 예전에 이 enum을 '수금유형'이라 적어 두었는데, 정본 23p가 요구하는 두 축은
 * <b>수금구분</b>({@code collKind} — 무슨 명목인가)과 <b>입금구분</b>({@code collType} — 어떤 형태인가)이다.
 * 이 enum은 그중 <b>입금구분</b>이다. 수금구분은 별도 컬럼({@code coll_kind})이다.
 */
public enum CollectionType {

    /** 현금 */
    CASH("현금"),

    /** 어음 — 이때만 어음번호·만기·은행/지점을 보관한다. */
    PROMISSORY("어음"),

    /** 선수금 */
    PREPAY("선수금"),

    /** 대체 */
    REPLACE("대체");

    private final String label;

    CollectionType(String label) {
        this.label = label;
    }

    /** 화면 표기명. 응답에 코드와 함께 실어 프론트가 매핑표를 들고 있지 않게 한다. */
    public String label() {
        return label;
    }
}
