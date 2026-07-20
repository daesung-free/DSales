package com.daesung.sales.consignment.entity;

import com.daesung.sales.common.entity.BaseEntity;
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
}
