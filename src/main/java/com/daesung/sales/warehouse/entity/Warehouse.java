package com.daesung.sales.warehouse.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.partner.entity.Partner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 창고. 🆕 근거: 시트2① (레거시 창고 0건, DB-22). */
@Entity
@Table(name = "warehouses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Warehouse extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WarehouseType type;

    /** 실물재고여부(false=가상/위탁 → 수불부 실재고 집계 제외). 기본 true. */
    @Column(name = "physical_stock", nullable = false)
    private boolean physicalStock = true;

    /** 소속거래처(위탁창고 1:1). 물류창고는 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_client_id")
    private Partner ownerClient;

    public static Warehouse create(String code, String name, WarehouseType type,
                                   boolean physicalStock, Partner ownerClient) {
        Warehouse w = new Warehouse();
        w.code = code;
        w.name = name;
        w.type = type;
        w.physicalStock = physicalStock;
        w.ownerClient = ownerClient;
        return w;
    }

    /** 수정(코드는 불변). */
    /**
     * 수정. 실물재고여부는 <b>null이면 기존 값을 유지</b>한다 —
     * primitive였을 때 요청에서 빠지면 false가 되어, 물류창고가 조용히 가상창고로 바뀌었다.
     * 그러면 제품수불부 실재고 집계에서 통째로 빠진다(Product의 같은 문제와 함께 수정, 2026-08-12).
     */
    public void update(String name, WarehouseType type, Boolean physicalStock, Partner ownerClient) {
        this.name = name;
        this.type = type;
        this.physicalStock = (physicalStock == null) ? this.physicalStock : physicalStock;
        this.ownerClient = ownerClient;
    }
}
