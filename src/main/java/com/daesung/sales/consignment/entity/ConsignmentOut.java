package com.daesung.sales.consignment.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.inventory.entity.InventoryTxn;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.product.entity.Product;
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

/**
 * 위탁 미결원장. 🆕 근거: 로직B (BE-30~35).
 * 불변식: total_qty = settled_qty + remaining_qty (DB CHECK로도 강제).
 */
@Entity
@Table(name = "consignment_out")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsignmentOut extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_out_no", nullable = false, unique = true, length = 30)
    private String sourceOutNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    @Column(name = "settled_qty", nullable = false)
    private int settledQty;

    @Column(name = "remaining_qty", nullable = false)
    private int remainingQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConsignmentStatus status = ConsignmentStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_txn_id")
    private InventoryTxn originTxn;

    public static ConsignmentOut create(String sourceOutNo, Product product, Partner partner,
                                        int totalQty, InventoryTxn originTxn) {
        ConsignmentOut c = new ConsignmentOut();
        c.sourceOutNo = sourceOutNo;
        c.product = product;
        c.partner = partner;
        c.totalQty = totalQty;
        c.settledQty = 0;
        c.remainingQty = totalQty;
        c.status = ConsignmentStatus.OPEN;
        c.originTxn = originTxn;
        return c;
    }

    /** 정산 역산(위탁정산 매출 취소 시). settled −, remaining + — 미결 잔여 복원. 불변식 유지. */
    public void unsettle(int qty) {
        this.settledQty -= qty;
        this.remainingQty += qty;
        this.status = (this.settledQty > 0) ? ConsignmentStatus.PARTIAL : ConsignmentStatus.OPEN;
    }

    /**
     * 미정산분 반품(위탁 반품). 미결 잔여(미판매분)를 위탁창고→물류창고로 되돌림.
     * total_qty·remaining_qty 동시 차감(불변식 total=settled+remaining 유지). 매출 무관.
     */
    public void returnUnsold(int qty) {
        if (qty > this.remainingQty) {
            throw new BusinessException(ErrorCode.OVER_SETTLEMENT,
                    "반품 수량이 미결(미판매) 잔여를 초과했습니다: 잔여 " + this.remainingQty + ", 요청 " + qty);
        }
        this.totalQty -= qty;
        this.remainingQty -= qty;
        if (this.remainingQty == 0) {
            this.status = ConsignmentStatus.CLOSED;
        } else if (this.settledQty > 0) {
            this.status = ConsignmentStatus.PARTIAL;
        } else {
            this.status = ConsignmentStatus.OPEN;
        }
    }

    /** 부분 정산. 불변식 total = settled + remaining 유지, 초과정산 방지. */
    public void settle(int qty) {
        if (qty > this.remainingQty) {
            throw new BusinessException(ErrorCode.OVER_SETTLEMENT,
                    "정산 수량이 미결 잔여를 초과했습니다: 잔여 " + this.remainingQty + ", 요청 " + qty);
        }
        this.settledQty += qty;
        this.remainingQty -= qty;
        this.status = (this.remainingQty == 0) ? ConsignmentStatus.CLOSED : ConsignmentStatus.PARTIAL;
    }
}
