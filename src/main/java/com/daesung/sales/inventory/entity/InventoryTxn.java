package com.daesung.sales.inventory.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.product.entity.Product;
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
}
