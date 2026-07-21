package com.daesung.sales.receivable.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.partner.entity.Partner;
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

/**
 * 채권 이월 스냅샷(carrData). carry_amount = 전년말 채권 잔액.
 * 레거시는 조회 부수효과로 생성 → 우리는 idempotent 배치/명시 API로만 생성. (연도,거래처) 유일.
 */
@Entity
@Table(name = "receivable_carryforward")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceivableCarryforward extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fiscal_year", nullable = false)
    private int fiscalYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(name = "carry_amount", nullable = false)
    private long carryAmount;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    public static ReceivableCarryforward create(int fiscalYear, Partner partner, long carryAmount,
                                                LocalDateTime generatedAt) {
        ReceivableCarryforward c = new ReceivableCarryforward();
        c.fiscalYear = fiscalYear;
        c.partner = partner;
        c.carryAmount = carryAmount;
        c.generatedAt = generatedAt;
        return c;
    }
}
