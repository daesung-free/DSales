package com.daesung.sales.sale.dto;

/**
 * 교재식 반품의 "반품 가능내역" 집계(거래처×도서×정가×공급률 단위).
 * 근거: 레거시 교재 반품 — 기존 출고내역(공급률·공급수량)을 먼저 조회 후 그 범위 내에서 차감.
 * 반품가능수량 = 누적 판매출고(SALE) − 기(旣)반품(RETURN). 취소건 제외.
 */
public interface ReturnableAgg {
    Long getProductId();

    String getProductCode();

    String getProductName();

    Integer getUnitPrice();

    Integer getSupplyRate();

    long getSaleQty();

    long getReturnQty();
}
