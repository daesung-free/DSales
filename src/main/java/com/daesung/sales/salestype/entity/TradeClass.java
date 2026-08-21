package com.daesung.sales.salestype.entity;

/**
 * <b>거래분류</b> — 발주처 표준 구분값 4축 중 첫째(2026-08-05 확정). 값은 5종:
 * 매출 · 무상 · 반품 · 입고 · 폐기.
 *
 * <p>근거: 레거시 {@code salesData.tradeType}({@code 조회.vb:1288} —
 * {@code min(tradeType) as '거래분류'}). 실측값은 매출·무상·반품·입고·취소·대체·출고로 더 어수선했는데,
 * 발주처가 위 5종으로 정리했다.
 *
 * <p>★<b>{@code tradeClass}라는 이름에 주의.</b> 레거시에는 같은 철자의 컬럼이 따로 있고
 * 그건 <b>상품군</b>(IC·모의고사·교재·특강·기타고사)이다. 우리 {@code Shipment.tradeClass}가
 * 그쪽이다. 이 enum은 그것과 무관한 <b>거래 성격</b> 축이다.
 *
 * <p>★다른 세 축과의 관계 — 넷은 서로 다른 질문에 답한다.
 * <pre>
 *   거래분류(5)   무슨 성격의 거래인가        매출/무상/반품/입고/폐기
 *   구분(상세)(3) 회계로 어떻게 처리되는가    {@link SalesCategory} SALE/FREE/RETURN
 *   출고유형(6)   어떤 사유로 나갔는가        {@link ShipmentType}
 *   창고(2)       실물이 어디에 있는가        WarehouseType MAIN/CONSIGN
 * </pre>
 * 매출 원장에서는 거래분류와 구분(상세)이 같은 값을 가리키므로 {@link #of}로 파생시킨다 —
 * 따로 저장하면 두 값이 어긋날 수 있고, 어긋나면 어느 쪽이 맞는지 알 수 없다.
 * 입고·폐기는 매출이 아니라 재고 원장({@code inventory_txn})에서 생기는 거래라 매출 조회에는 나오지 않는다.
 */
public enum TradeClass {

    SALES("매출"),
    FREE("무상"),
    RETURN("반품"),

    /** 재고 원장에서 생긴다. 매출 조회에는 나오지 않는다. */
    INBOUND("입고"),

    /** 재고 원장에서 생긴다. 매출 조회에는 나오지 않는다. */
    DISPOSE("폐기");

    private final String label;

    TradeClass(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 회계구분 → 거래분류. 매출 원장의 거래분류는 이 하나에서만 나온다(저장하지 않는다). */
    public static TradeClass of(SalesCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case SALE -> SALES;
            case FREE -> FREE;
            case RETURN -> RETURN;
        };
    }

    /**
     * 거래분류 → 회계구분(매출 조회 필터용). 매출 원장에 대응이 없으면(입고·폐기) null.
     *
     * <p>null이면 호출부는 <b>빈 결과</b>를 돌려줘야 한다. 조건을 무시하고 전체를 주면
     * 담당자가 '폐기'로 걸렀는데 매출이 잔뜩 나오는 꼴이 된다.
     */
    public SalesCategory toSalesCategory() {
        return switch (this) {
            case SALES -> SalesCategory.SALE;
            case FREE -> SalesCategory.FREE;
            case RETURN -> SalesCategory.RETURN;
            case INBOUND, DISPOSE -> null;
        };
    }
}
