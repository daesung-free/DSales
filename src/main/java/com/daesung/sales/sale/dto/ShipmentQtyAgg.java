package com.daesung.sales.sale.dto;

import java.time.LocalDate;

/** 발송 단위 상품군별 수량 집계(작업결과의 교재·IC 컬럼). */
public interface ShipmentQtyAgg {
    LocalDate getTradeDate();

    Long getPartnerId();

    String getSchoolCode();

    String getTradeClass();

    Long getQty();
}
