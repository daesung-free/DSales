package com.daesung.sales.consignment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 위탁정산 초안의 상세행 1줄. 대상 미결 + 이번에 정산할 수량·금액. */
@Entity
@Table(name = "settlement_draft_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementDraftLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_id", nullable = false)
    private SettlementDraft draft;

    /** 대상 미결. 화면의 pendingId. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consignment_out_id", nullable = false)
    private ConsignmentOut consignmentOut;

    @Column(name = "settle_qty", nullable = false)
    private int settleQty;

    @Column(name = "unit_price")
    private Integer unitPrice;

    @Column(name = "supply_rate")
    private Integer supplyRate;

    @Column(name = "supply_amount", nullable = false)
    private long supplyAmount;

    @Column(nullable = false)
    private long tax;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    public static SettlementDraftLine of(ConsignmentOut out, int settleQty, Integer unitPrice,
                                         Integer supplyRate, long supplyAmount, long tax) {
        SettlementDraftLine l = new SettlementDraftLine();
        l.consignmentOut = out;
        l.settleQty = settleQty;
        l.unitPrice = unitPrice;
        l.supplyRate = supplyRate;
        l.supplyAmount = supplyAmount;
        l.tax = tax;
        l.totalAmount = supplyAmount + tax;
        return l;
    }

    void attachTo(SettlementDraft draft) {
        this.draft = draft;
    }
}
