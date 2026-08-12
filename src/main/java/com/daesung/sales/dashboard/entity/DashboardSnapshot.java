package com.daesung.sales.dashboard.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대시보드 월별 실적 스냅샷. 근거: 발주처 확정(3-2 아) "하루 1회 갱신".
 *
 * <p>축은 {@link SalesTarget}과 맞춘다 — 목표와 실적을 같은 키로 묶어야 달성률이 나온다.
 * 다만 사업부문(DIVISION)은 아직 매출을 그 단위로 집계할 수 없어(상품마스터 매출구분과
 * 목표 기준대상의 이름이 어긋나 있다) 전사·상품만 담는다.
 */
@Entity
@Table(name = "dashboard_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DashboardSnapshot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fiscal_year", nullable = false)
    private int fiscalYear;

    @Column(nullable = false)
    private int month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TargetScope scope;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "net_sales", nullable = false)
    private long netSales;

    /** 이 값을 계산한 시각. 화면이 "언제 기준 숫자인지" 보여줄 수 있어야 한다. */
    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    public static DashboardSnapshot of(int year, int month, TargetScope scope, Long productId,
                                       long netSales, LocalDateTime computedAt) {
        DashboardSnapshot s = new DashboardSnapshot();
        s.fiscalYear = year;
        s.month = month;
        s.scope = scope;
        s.productId = (scope == TargetScope.PRODUCT) ? productId : null;
        s.netSales = netSales;
        s.computedAt = computedAt;
        return s;
    }

    public void refresh(long netSales, LocalDateTime computedAt) {
        this.netSales = netSales;
        this.computedAt = computedAt;
    }
}
