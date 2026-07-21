package com.daesung.sales.sale.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.consignment.entity.ConsignmentSettlement;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매출 원장(확정 매출 라인). 근거: 기획서 P.12/P.13, API 스펙 §3.
 * 미결은 여기 없음 — consignment_out(OPEN/PARTIAL/CLOSED)이 단일 진실.
 */
@Entity
@Table(name = "sales")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sale extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_no", unique = true, length = 30)
    private String salesNo;

    @Column(name = "sales_date", nullable = false)
    private LocalDate salesDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 매출유형(독립 축): 일반/위탁매출. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sales_type", nullable = false, length = 20)
    private SalesType salesType;

    /** 출고유형(6종). */
    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_type", nullable = false, length = 20)
    private ShipmentType shipmentType;

    /** 구분(회계): 매출/무상/반품. */
    @Enumerated(EnumType.STRING)
    @Column(name = "sales_category", nullable = false, length = 10)
    private SalesCategory salesCategory;

    @Column(name = "unit_price")
    private Integer unitPrice;

    @Column(name = "supply_rate")
    private Integer supplyRate;

    /** 수량(반품/취소는 음수). */
    @Column(nullable = false)
    private int qty;

    @Column(name = "supply_amount")
    private Long supplyAmount;

    private Long tax;

    @Column(name = "total_amount")
    private Long totalAmount;

    /** 원본 위탁출고번호(위탁정산 매출일 때). */
    @Column(name = "source_out_no", length = 30)
    private String sourceOutNo;

    /** 위탁 정산 이력 링크(위탁정산 매출일 때). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private ConsignmentSettlement settlement;

    @Column(length = 1000)
    private String memo;

    /** 일반(직접) 매출 라인 생성. 위탁 정산 매출은 별도(from-consign)로 생성. */
    public static Sale create(String salesNo, LocalDate salesDate, Partner partner, Product product,
                              SalesType salesType, ShipmentType shipmentType, SalesCategory salesCategory,
                              Integer unitPrice, Integer supplyRate, int qty,
                              Long supplyAmount, Long tax, Long totalAmount, String memo) {
        Sale s = new Sale();
        s.salesNo = salesNo;
        s.salesDate = salesDate;
        s.partner = partner;
        s.product = product;
        s.salesType = salesType;
        s.shipmentType = shipmentType;
        s.salesCategory = salesCategory;
        s.unitPrice = unitPrice;
        s.supplyRate = supplyRate;
        s.qty = qty;
        s.supplyAmount = supplyAmount;
        s.tax = tax;
        s.totalAmount = totalAmount;
        s.memo = memo;
        return s;
    }
}
