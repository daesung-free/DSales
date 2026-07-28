package com.daesung.sales.product.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 거래처별 단가·노출 매핑(도서관리 3번째 탭). 근거: 요구사항 32~34p.
 * 도서×거래처 단위로 공급률(→단가 파생)과 노출 여부를 관리. 매출등록 시 공급률 자동조회 기반.
 */
@Entity
@Table(name = "product_partner_price",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_partner",
                columnNames = {"product_id", "partner_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductPartnerPrice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    /** 거래처별 공급률(%). 단가 = 도서 정가 × 공급률/100. */
    @Column(name = "supply_rate")
    private Integer supplyRate;

    /** 거래처별 노출 여부(false=해당 거래처에 미노출). 기본 true. */
    @Column(nullable = false)
    private boolean visible = true;

    public static ProductPartnerPrice create(Product product, Partner partner, Integer supplyRate, boolean visible) {
        ProductPartnerPrice m = new ProductPartnerPrice();
        m.product = product;
        m.partner = partner;
        m.supplyRate = supplyRate;
        m.visible = visible;
        return m;
    }

    public void update(Integer supplyRate, boolean visible) {
        this.supplyRate = supplyRate;
        this.visible = visible;
    }
}
