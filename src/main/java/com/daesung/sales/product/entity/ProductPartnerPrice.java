package com.daesung.sales.product.entity;

import com.daesung.sales.common.entity.SoftDeletableEntity;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 거래처별 단가·노출 매핑(도서관리 3번째 탭). 근거: 요구사항 32~34p.
 * 도서×거래처 단위로 공급률(→단가 파생)과 노출 여부를 관리. 매출등록 시 공급률 자동조회 기반.
 *
 * <p>논리삭제 대상 — 매핑 삭제 API가 물리 DELETE 하던 자리다(게이트규칙 위반).
 * 유니크 키는 Flyway V25에서 {@code (product_id, partner_id, del_key)}로 재정의돼 있어
 * 삭제 후 같은 도서×거래처를 다시 등록할 수 있다.
 */
@Entity
@Table(name = "product_partner_price")   // 유니크 제약은 Flyway V25 소유(del_key 생성컬럼 포함)
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductPartnerPrice extends SoftDeletableEntity {

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
