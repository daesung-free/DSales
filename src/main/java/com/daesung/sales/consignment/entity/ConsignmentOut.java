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

    /**
     * 아직 살아 있는 출고량. 불변식 {@code total = settled + remaining}을 지키려고
     * 반품하면 함께 줄어든다 — "처음 얼마 나갔나"는 {@link #originalQty}가 갖는다.
     */
    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    /** 처음 출고한 수량(불변). 반품해도 줄지 않는다. 원출고 = 정산 + 반품 + 미결잔여. */
    @Column(name = "original_qty", nullable = false)
    private int originalQty;

    /** 반품 누적. 이게 없으면 반품 뒤에 "처음부터 그만큼만 나간 것"처럼 보인다. */
    @Column(name = "returned_qty", nullable = false)
    private int returnedQty;

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
        c.originalQty = totalQty;
        c.returnedQty = 0;
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
        this.returnedQty += qty;   // 원출고 = 정산 + 반품 + 미결잔여 가 읽히도록 누적
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
