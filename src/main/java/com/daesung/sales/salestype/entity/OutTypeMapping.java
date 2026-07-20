package com.daesung.sales.salestype.entity;

import com.daesung.sales.warehouse.entity.WarehouseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 출고유형(6) → 회계구분(3) 룩업. 근거: 시트2① (레거시 하드코딩 CASE 대체, DB-25).
 * ※ '취소'의 공급률 30% 분기는 서비스 로직에서 처리(스키마 밖).
 */
@Entity
@Table(name = "out_type_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutTypeMapping {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "out_type", length = 20)
    private ShipmentType shipmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "acct_type", nullable = false, length = 10)
    private SalesCategory salesCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_wh_type", nullable = false, length = 10)
    private WarehouseType defaultWhType;

    @Column(name = "is_consignment", nullable = false)
    private boolean consignment;
}
