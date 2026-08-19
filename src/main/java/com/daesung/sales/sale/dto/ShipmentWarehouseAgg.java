package com.daesung.sales.sale.dto;

import com.daesung.sales.warehouse.entity.WarehouseType;
import java.time.LocalDate;

/** 발송 단위의 출고 창고(작업결과 '출고창고'). */
public interface ShipmentWarehouseAgg {
    LocalDate getTradeDate();

    Long getPartnerId();

    String getSchoolCode();

    String getTradeClass();

    Long getWarehouseId();

    String getWarehouseName();

    WarehouseType getWarehouseType();
}
