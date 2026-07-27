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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매출 원장(확정 매출 라인). 근거: 기획서 P.12/P.13, API 스펙 §3.
 * 미결은 여기 없음 — consignment_out(OPEN/PARTIAL/CLOSED)이 단일 진실. 취소는 논리 취소(canceled).
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

    /** 성적처리 구분(37p 월별매출액명세서 인원 집계축). null=비처리로 간주. */
    @Enumerated(EnumType.STRING)
    @Column(name = "proc_type", length = 20)
    private ProcType procType;

    /** 원본 위탁출고번호(위탁정산 매출일 때). */
    @Column(name = "source_out_no", length = 30)
    private String sourceOutNo;

    /** 위탁 정산 이력 링크(위탁정산 매출일 때). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private ConsignmentSettlement settlement;

    @Column(length = 1000)
    private String memo;

    /** DSRE 매출일괄등록 소스키(req_cd:lst_cd:dtl_cd:req_gn). 멱등 dedup용. 일반 매출은 null. */
    @Column(name = "bulk_import_key", length = 60)
    private String bulkImportKey;

    /** 논리 취소 여부(물리삭제 아님, 이력 보존). */
    @Column(nullable = false)
    private boolean canceled = false;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    /** 일반(직접) 매출 라인 생성. 위탁 정산 매출은 별도(from-consign)로 생성. procType은 성적처리 구분(nullable). */
    public static Sale create(String salesNo, LocalDate salesDate, Partner partner, Product product,
                              SalesType salesType, ShipmentType shipmentType, SalesCategory salesCategory,
                              Integer unitPrice, Integer supplyRate, int qty,
                              Long supplyAmount, Long tax, Long totalAmount, ProcType procType, String memo) {
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
        s.procType = procType;
        s.memo = memo;
        return s;
    }

    /** 위탁 정산 확정 매출 라인. sales_type=위탁, shipment_type=위탁출고, 회계=매출. */
    public static Sale createConsign(String salesNo, LocalDate salesDate, Partner partner, Product product,
                                     Integer unitPrice, Integer supplyRate, int qty,
                                     Long supplyAmount, Long tax, Long totalAmount,
                                     String sourceOutNo, ConsignmentSettlement settlement, String memo) {
        Sale s = new Sale();
        s.salesNo = salesNo;
        s.salesDate = salesDate;
        s.partner = partner;
        s.product = product;
        s.salesType = SalesType.CONSIGN_SALES;
        s.shipmentType = ShipmentType.CONSIGN_SHIP;
        s.salesCategory = SalesCategory.SALE;
        s.unitPrice = unitPrice;
        s.supplyRate = supplyRate;
        s.qty = qty;
        s.supplyAmount = supplyAmount;
        s.tax = tax;
        s.totalAmount = totalAmount;
        s.sourceOutNo = sourceOutNo;
        s.settlement = settlement;
        s.memo = memo;
        return s;
    }

    /** DSRE 매출일괄등록 라인. 재무 매출(재고 미반영). bulkImportKey로 멱등. */
    public static Sale createBulk(String salesNo, LocalDate salesDate, Partner partner, Product product,
                                  ShipmentType shipmentType, SalesCategory salesCategory,
                                  Integer unitPrice, Integer supplyRate, int qty,
                                  Long supplyAmount, Long tax, Long totalAmount, String memo, String bulkImportKey) {
        Sale s = new Sale();
        s.salesNo = salesNo;
        s.salesDate = salesDate;
        s.partner = partner;
        s.product = product;
        s.salesType = SalesType.NORMAL_SALES;
        s.shipmentType = shipmentType;
        s.salesCategory = salesCategory;
        s.unitPrice = unitPrice;
        s.supplyRate = supplyRate;
        s.qty = qty;
        s.supplyAmount = supplyAmount;
        s.tax = tax;
        s.totalAmount = totalAmount;
        s.memo = memo;
        s.bulkImportKey = bulkImportKey;
        return s;
    }

    /** 논리 취소. */
    public void cancel() {
        this.canceled = true;
        this.canceledAt = LocalDateTime.now();
    }
}
