package com.daesung.sales.inventory.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.product.entity.Product;
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

/** 재고실사 명세(상품별). system(캐시)↔counted(실물) 대조, diff=조정량. */
@Entity
@Table(name = "stocktake_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StocktakeLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stocktake_id", nullable = false)
    private Stocktake stocktake;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "system_qty", nullable = false)
    private int systemQty;

    @Column(name = "counted_qty", nullable = false)
    private int countedQty;

    @Column(nullable = false)
    private int diff;

    static StocktakeLine of(Stocktake stocktake, Product product, int systemQty, int countedQty) {
        StocktakeLine l = new StocktakeLine();
        l.stocktake = stocktake;
        l.product = product;
        l.systemQty = systemQty;
        l.countedQty = countedQty;
        l.diff = countedQty - systemQty;
        return l;
    }
}
