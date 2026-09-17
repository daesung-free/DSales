package com.daesung.sales.sale.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.consignment.entity.ConsignmentSettlement;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import com.daesung.sales.warehouse.entity.Warehouse;
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

    /**
     * 출고 창고(7p 재고위치 · 27p 출고창고).
     *
     * <p>지금까지 매출등록에서 창고를 받아 재고만 차감하고 버렸다 — 나중에 "이 매출이 어느 창고에서
     * 나갔나"를 되짚을 수 없었다. 위탁정산 매출은 위탁창고에서 나간다.
     * 이 컬럼이 생기기 전 데이터는 알 수 없어 null이다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

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

    /**
     * 적용된 권당 할인액(34p). 값이 있으면 <b>공급가액 = (정가−할인액) × 수량</b>이고
     * 공급률은 금액에 쓰이지 않는다(레거시 매출가져오기.vb:425 — 둘 중 하나만 쓴다).
     *
     * <p>거래처×대분류 매핑에서 오지만 매핑은 언제든 바뀌므로, 정가·공급률과 같은 이유로
     * 매출 라인에 복사해 둔다 — 그래야 "이 건의 금액이 왜 이 값인가"를 나중에 설명할 수 있다.
     */
    @Column(name = "discount_amount")
    private Integer discountAmount;

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

    /** 학교/학원 코드(레거시 salesData.schCode). 매출 업로드 등에서 세부 거래단위. */
    @Column(name = "school_code", length = 30)
    private String schoolCode;

    /** 학교/학원명(레거시 salesData.schName). */
    @Column(name = "school_name", length = 100)
    private String schoolName;

    /** 세트 상품 회차(레거시 salesData.bookReqSeq). */
    /** 포장구분(개별1/개별2/반별) — IC회차별작업현황 집계축. 근거: 레거시 distData.packType. */
    @Enumerated(EnumType.STRING)
    @Column(name = "pack_type", length = 20)
    private PackType packType;

    /**
     * 무상 세부구분(학생용/교사용/M+/IC+). 레거시 {@code salesData.part}.
     *
     * <p>★출고유형(shipmentType)과 <b>다른 축</b>이다. 출고유형은 "무상이냐 매출이냐"를,
     * 이건 "그 무상이 누구 몫이냐"를 가른다. 제품수불부 무상 4칸이 이 값으로 갈린다.
     * 무상이 아닌 건에는 보통 비어 있다.
     */
    @Column(name = "part", length = 30)
    private String part;

    /** 무상 세부구분 지정. 등록·업로드에서 호출. */
    public void applyPart(String part) {
        this.part = part;
    }

    @Column(name = "book_round")
    private Integer bookRound;

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

    /** 반품 라인에 원본 출고번호 링크(역추적용). */
    public void linkSourceOut(String sourceOutNo) {
        this.sourceOutNo = sourceOutNo;
    }

    /** 매출 업로드 세부(학교·회차) 설정. */
    /** 포장구분 지정(물류 작업현황 집계용). */
    /** 출고 창고 지정(매출등록·위탁정산에서 호출). */
    public void applyWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    /** 적용된 할인액 기록(매출등록·업로드·위탁정산·반품입고에서 호출). 할인 없으면 호출하지 않는다. */
    public void applyDiscount(Integer discountAmount) {
        this.discountAmount = discountAmount;
    }

    public void applyPackType(PackType packType) {
        this.packType = packType;
    }

    public void applyUploadDetail(String schoolCode, String schoolName, Integer bookRound) {
        this.schoolCode = schoolCode;
        this.schoolName = schoolName;
        this.bookRound = bookRound;
    }

    /** 논리 취소. */
    public void cancel() {
        this.canceled = true;
        this.canceledAt = LocalDateTime.now();
    }
}
