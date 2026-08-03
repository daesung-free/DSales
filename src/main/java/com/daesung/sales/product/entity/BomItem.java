package com.daesung.sales.product.entity;

import com.daesung.sales.common.entity.SoftDeletableEntity;
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
 * BOM 구성: 완제품(parent) 1개 = 구성품(child) ratio개. 🆕 근거: 시트2 BOM 가변비율.
 *
 * <p>논리삭제 대상 — BOM 재등록은 기존 구성을 물리 DELETE 하던 자리다(게이트규칙 위반).
 * 삭제행은 남기되 {@code @SQLRestriction}으로 조회에서 자동 제외한다. 거래 기록이 bom_items를
 * FK로 참조하지 않아(조립 이력은 inventory_txn에 남음) 전역 필터가 안전하다.
 */
@Entity
@Table(name = "bom_items")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BomItem extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_product_id", nullable = false)
    private Product parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_product_id", nullable = false)
    private Product child;

    @Column(nullable = false)
    private int ratio;


    public static BomItem create(Product parent, Product child, int ratio) {
        BomItem b = new BomItem();
        b.parent = parent;
        b.child = child;
        b.ratio = ratio;
        return b;
    }

}
