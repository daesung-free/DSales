package com.daesung.sales.warehouse.entity;

/** 창고 유형. MAIN=본사 물류창고(실물), CONSIGN=위탁·관리창고(가상). 근거: 시트2① / API 스펙. */
public enum WarehouseType {
    MAIN,
    CONSIGN
}
