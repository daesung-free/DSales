package com.daesung.sales.inventory.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.warehouse.entity.Warehouse;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 재고실사 헤더(신규, §7G). 실물 카운트를 캐시와 대조 → 차이는 ADJUST 이벤트로 조정. */
@Entity
@Table(name = "stocktake")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stocktake extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stocktake_no", unique = true, length = 30)
    private String stocktakeNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "stocktake_date", nullable = false)
    private LocalDate stocktakeDate;

    @Column(length = 1000)
    private String memo;

    @OneToMany(mappedBy = "stocktake", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StocktakeLine> lines = new ArrayList<>();

    public static Stocktake create(String stocktakeNo, Warehouse warehouse, LocalDate stocktakeDate, String memo) {
        Stocktake s = new Stocktake();
        s.stocktakeNo = stocktakeNo;
        s.warehouse = warehouse;
        s.stocktakeDate = stocktakeDate;
        s.memo = memo;
        return s;
    }

    /** 실사 라인 추가(diff는 counted−system). */
    public StocktakeLine addLine(Product product, int systemQty, int countedQty) {
        StocktakeLine line = StocktakeLine.of(this, product, systemQty, countedQty);
        this.lines.add(line);
        return line;
    }
}
