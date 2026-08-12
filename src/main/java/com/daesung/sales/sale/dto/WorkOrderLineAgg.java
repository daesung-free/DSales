package com.daesung.sales.sale.dto;

import java.time.LocalDate;

/** 발송 단위 도서별 상세(작업요청서 지시 목록). */
public interface WorkOrderLineAgg {
    LocalDate getTradeDate();

    Long getPartnerId();

    String getSchoolCode();

    String getTradeClass();

    String getCatCode();

    String getProductCode();

    String getProductName();

    Integer getBookRound();

    Integer getUnitPrice();

    Integer getSupplyRate();

    Long getQty();

    Long getAmount();
}
