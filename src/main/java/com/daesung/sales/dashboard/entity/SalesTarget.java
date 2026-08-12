package com.daesung.sales.dashboard.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매출목표(신규, DB-28). (연도, 월|연간, 대상) 단위.
 *
 * <p>대상은 세 층이다 — 전사 / 사업부문 / 상품. 사업부문 층은 발주처가 준 실제 목표가
 * 그 단위여서 생겼다(자료요청서 1-6 회신: 더프리미엄 94억, 학원 컨텐츠 20억 …).
 *
 * <p>{@code month}가 null이면 <b>연간 목표</b>다. 처음엔 월 필수였는데, 우리가 요청 양식을
 * 월 단위 예시로 보낸 탓에 구조가 그렇게 굳었고 실제 회신은 연간으로 왔다.
 */
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

    /** 목표 월(1~12). null이면 연간 목표. */
    @Column
    private Integer month;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TargetScope scope = TargetScope.COMPANY;

    /** scope=DIVISION일 때 사업부문명. 그 외에는 null. */
    @Column(name = "scope_key", length = 100)
    private String scopeKey;

    /** scope=PRODUCT일 때 상품 id. 그 외에는 null. */
    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private TargetEntryType entryType = TargetEntryType.TARGET;

    @Column(name = "target_amount", nullable = false)
    private long targetAmount;

    public static SalesTarget create(int fiscalYear, Integer month, TargetScope scope, String scopeKey,
                                     Long productId, TargetEntryType entryType, long amount) {
        SalesTarget t = new SalesTarget();
        t.fiscalYear = fiscalYear;
        t.month = month;
        t.scope = (scope == null) ? TargetScope.COMPANY : scope;
        // 축과 값이 어긋나면 조회가 조용히 빗나간다 — 저장 시점에 정리한다.
        t.scopeKey = (t.scope == TargetScope.DIVISION) ? scopeKey : null;
        t.productId = (t.scope == TargetScope.PRODUCT) ? productId : null;
        t.entryType = (entryType == null) ? TargetEntryType.TARGET : entryType;
        t.targetAmount = amount;
        return t;
    }

    public void updateAmount(long targetAmount) {
        this.targetAmount = targetAmount;
    }

    /** 연간 목표인지(월 목표가 아닌지). */
    public boolean isAnnual() {
        return month == null;
    }
}
