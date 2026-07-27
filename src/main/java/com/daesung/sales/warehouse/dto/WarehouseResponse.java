package com.daesung.sales.warehouse.dto;

import com.daesung.sales.warehouse.entity.Warehouse;
import com.daesung.sales.warehouse.entity.WarehouseType;

/** 창고 응답 DTO. */
public record WarehouseResponse(Long id, String code, String name, WarehouseType type,
                                boolean physicalStock, Long ownerClientId, String ownerClientName) {
    public static WarehouseResponse from(Warehouse w) {
        var owner = w.getOwnerClient();
        return new WarehouseResponse(w.getId(), w.getCode(), w.getName(), w.getType(),
                w.isPhysicalStock(),
                owner != null ? owner.getId() : null,
                owner != null ? owner.getName() : null);
    }
}
