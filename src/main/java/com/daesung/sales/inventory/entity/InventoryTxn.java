package com.daesung.sales.inventory.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.product.entity.Product;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재고 이벤트. 근거: InvenData 정규화 + 🆕 창고/원가/txn_type.
 * qty는 부호 포함(입고/반품 +, 출고/폐기 -). 창고 잔량 = 상품×창고 qty 합.
 */
@Entity
@Table(name = "inventory_txn")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryTxn extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 20)
    private TxnType txnType;

    @Column(nullable = false)
    private int qty;

    /** 🆕 입고원가(순매출/이익률용). */
    @Column(name = "unit_cost")
    private Long unitCost;

    @Column(name = "ref_no", length = 30)
    private String refNo;

    /** 출고/반품 이벤트의 출고유형 태그(수불부 매출/무상/교사용/반품 버킷 분해용). 그 외 이벤트는 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_type", length = 20)
    private ShipmentType shipmentType;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    /** 이고/BOM 짝 연결. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_txn_id")
    private InventoryTxn sourceTxn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private Partner partner;

    @Column(length = 1000)
    private String memo;

    /** 일반 입고 이벤트. qty는 양수. */
    public static InventoryTxn inbound(Product product, Warehouse warehouse, int qty, Long unitCost,
                                       LocalDate tradeDate, Partner partner, String memo) {
        InventoryTxn t = new InventoryTxn();
        t.product = product;
        t.warehouse = warehouse;
        t.txnType = TxnType.INBOUND;
        t.qty = qty;
        t.unitCost = unitCost;
        t.tradeDate = tradeDate;
        t.partner = partner;
        t.memo = memo;
        return t;
    }

    /** 이고(창고 이동) 한 다리. 출발=−qty(sourceTxn=null), 도착=+qty(sourceTxn=출발 이벤트). */
    public static InventoryTxn transfer(Product product, Warehouse warehouse, int qty,
                                        LocalDate tradeDate, InventoryTxn sourceTxn, String memo) {
        InventoryTxn t = new InventoryTxn();
        t.product = product;
        t.warehouse = warehouse;
        t.txnType = TxnType.TRANSFER;
        t.qty = qty;
        t.tradeDate = tradeDate;
        t.sourceTxn = sourceTxn;
        t.memo = memo;
        return t;
    }

    /** BOM 조립/해체 한 다리. txnType=BOM_ASSEMBLE|BOM_DISASSEMBLE, qty는 부호 포함. */
    public static InventoryTxn bom(Product product, Warehouse warehouse, int qty,
                                   TxnType txnType, LocalDate tradeDate, String memo) {
        InventoryTxn t = new InventoryTxn();
        t.product = product;
        t.warehouse = warehouse;
        t.txnType = txnType;
        t.qty = qty;
        t.tradeDate = tradeDate;
        t.memo = memo;
        return t;
    }

    /**
     * 출고/반품(매출 연동). 정상출고=OUTBOUND(qty 음수), 반품=RETURN(qty 양수).
     * shipmentType을 실어 수불부가 매출/무상/교사용/반품으로 분해. refNo=매출번호(I-...).
     */
    public static InventoryTxn shipment(Product product, Warehouse warehouse, int qty, TxnType txnType,
                                        ShipmentType shipmentType, LocalDate tradeDate, String refNo, String memo) {
        InventoryTxn t = new InventoryTxn();
        t.product = product;
        t.warehouse = warehouse;
        t.txnType = txnType;
        t.qty = qty;
        t.shipmentType = shipmentType;
        t.tradeDate = tradeDate;
        t.refNo = refNo;
        t.memo = memo;
        return t;
    }

    /** 폐기. qty는 음수(재고 차감). refNo=폐기번호(P-...). */
    public static InventoryTxn dispose(Product product, Warehouse warehouse, int qty,
                                       LocalDate tradeDate, String refNo, String memo) {
        InventoryTxn t = new InventoryTxn();
        t.product = product;
        t.warehouse = warehouse;
        t.txnType = TxnType.DISPOSE;
        t.qty = qty;
        t.tradeDate = tradeDate;
        t.refNo = refNo;
        t.memo = memo;
        return t;
    }

    /** 재고실사 조정. qty=실물−시스템(부호 포함). refNo=실사번호(ST-...). */
    public static InventoryTxn adjust(Product product, Warehouse warehouse, int qty,
                                      LocalDate tradeDate, String refNo, String memo) {
        InventoryTxn t = new InventoryTxn();
        t.product = product;
        t.warehouse = warehouse;
        t.txnType = TxnType.ADJUST;
        t.qty = qty;
        t.tradeDate = tradeDate;
        t.refNo = refNo;
        t.memo = memo;
        return t;
    }
}
