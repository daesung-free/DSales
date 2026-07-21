package com.daesung.sales.consignment.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 위탁 부분정산 이력. 🆕 1건의 위탁출고에 여러 차례 분할 정산. 근거: 로직B. */
@Entity
@Table(name = "consignment_settlement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsignmentSettlement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consignment_out_id", nullable = false)
    private ConsignmentOut consignmentOut;

    @Column(name = "settle_qty", nullable = false)
    private int settleQty;

    @Column(name = "sales_ref_no", length = 30)
    private String salesRefNo;

    @Column(name = "settled_at", nullable = false)
    private LocalDateTime settledAt;

    public static ConsignmentSettlement create(ConsignmentOut consignmentOut, int settleQty,
                                               String salesRefNo, LocalDateTime settledAt) {
        ConsignmentSettlement s = new ConsignmentSettlement();
        s.consignmentOut = consignmentOut;
        s.settleQty = settleQty;
        s.salesRefNo = salesRefNo;
        s.settledAt = settledAt;
        return s;
    }
}
