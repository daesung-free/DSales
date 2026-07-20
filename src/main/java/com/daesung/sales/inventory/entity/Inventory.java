package com.daesung.sales.inventory.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.warehouse.entity.Warehouse;
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

/** 재고 원장(잔량) — 상품×창고. 🆕 음수재고 방지. */
@Entity
@Table(name = "inventory",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "warehouse_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(nullable = false)
    private int qty;

    public static Inventory create(Product product, Warehouse warehouse, int qty) {
        Inventory inv = new Inventory();
        inv.product = product;
        inv.warehouse = warehouse;
        inv.qty = qty;
        return inv;
    }

    /** 잔량 증감(입고 +, 출고 -). 음수재고는 불변식 위반 → 예외. */
    public void addQty(int delta) {
        int result = this.qty + delta;
        if (result < 0) {
            throw new BusinessException(ErrorCode.NEGATIVE_STOCK,
                    "재고 부족: 현재 " + this.qty + ", 요청 " + delta);
        }
        this.qty = result;
    }
}
