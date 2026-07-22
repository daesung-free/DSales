package com.daesung.sales.dashboard.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 매출목표(신규, DB-28). (연도,월,상품) 단위. product_id=null=전사 월목표. */
@Entity
@Table(name = "sales_target")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesTarget extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fiscal_year", nullable = false)
    private int fiscalYear;

    @Column(nullable = false)
    private int month;

    /** null=전사 월목표, 값=상품별 목표. */
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "target_amount", nullable = false)
    private long targetAmount;

    public static SalesTarget create(int fiscalYear, int month, Long productId, long targetAmount) {
        SalesTarget t = new SalesTarget();
        t.fiscalYear = fiscalYear;
        t.month = month;
        t.productId = productId;
        t.targetAmount = targetAmount;
        return t;
    }

    public void updateAmount(long targetAmount) {
        this.targetAmount = targetAmount;
    }
}
