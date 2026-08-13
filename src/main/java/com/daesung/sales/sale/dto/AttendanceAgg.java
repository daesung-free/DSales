package com.daesung.sales.sale.dto;

/** 응시현황(연도별) 원자료 — 거래처×월 수량·금액. */
public interface AttendanceAgg {
    Long getPartnerId();

    String getPartnerCode();

    String getPartnerName();

    String getCityName();

    Integer getMonth();

    Long getQty();

    Long getAmount();
}
