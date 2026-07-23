package com.daesung.sales.sale.dto;

/**
 * 과목별매출현황 집계 원천(거래처×분류×도서). 근거: 레거시 과목별매출현황.vb.
 * 매출/반품/교사용 수량 버킷은 salesCategory(SALE/RETURN/FREE)로 분기.
 * 순매출·반품률은 서비스에서 계산.
 */
public interface CategorySalesAgg {
    Long getPartnerId();

    String getPartnerCode();

    String getPartnerName();

    String getCatCode();

    String getCatName();

    String getBookCode();

    String getBookName();

    long getSaleQty();

    long getReturnQty();

    long getTeacherQty();
}
