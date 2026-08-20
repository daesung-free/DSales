package com.daesung.sales.product.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 세부구분(구 '매출구분') 마스터. 근거: 발주처 회신 2026-08-20.
 *
 * <p>회신 원문: "대분류 하위에 속하는 값이고, 명칭 자체가 연도별로 바뀔 수 있어
 * 사용자가 직접 추가/삭제할 수 있는 관리형 값". 그래서 자유 문자열이 아니라 표로 둔다.
 *
 * <p><b>세부구분 → 대분류 매핑을 가진 유일한 곳이다.</b> 상품에는 대분류를 복사해두지 않는다.
 * 복사해두면 매핑이 바뀔 때 둘이 어긋나고, 어느 쪽이 맞는지 알 수 없어진다.
 */
@Entity
@Table(name = "sales_divisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesDivision extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 불변 키. {@code products.sales_division}이 이 값을 가리킨다.
     *
     * <p>명칭과 따로 두는 이유: 회신이 "명칭은 연도별로 바뀔 수 있다"고 했다.
     * 명칭을 키로 쓰면 이름을 고치는 순간 그 구분을 쓰던 상품들이 전부 연결을 잃는다.
     */
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    /** 표시 명칭. 바뀔 수 있다. */
    @Column(nullable = false, length = 50)
    private String name;

    /** 소속 대분류 — 집계 기준. */
    @Enumerated(EnumType.STRING)
    @Column(name = "major_category", nullable = false, length = 20)
    private MajorCategory majorCategory;

    @Column(name = "use_yn", nullable = false)
    private boolean useYn = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static SalesDivision create(String code, String name, MajorCategory majorCategory,
                                       Boolean useYn, Integer sortOrder) {
        SalesDivision d = new SalesDivision();
        d.code = code;
        d.name = name;
        d.majorCategory = majorCategory;
        d.useYn = (useYn == null) || useYn;
        d.sortOrder = (sortOrder == null) ? 0 : sortOrder;
        return d;
    }

    /**
     * 수정. <b>코드는 바꾸지 않는다</b> — 상품이 그 값으로 연결돼 있다.
     *
     * <p>{@code useYn}은 null이면 기존 값을 유지한다. 일부 필드만 보낸 PUT이
     * 사용 중인 구분을 조용히 끄면, 상품 목록에서 그 구분이 사라진 이유를 알 수 없다.
     */
    public void update(String name, MajorCategory majorCategory, Boolean useYn, Integer sortOrder) {
        this.name = name;
        this.majorCategory = majorCategory;
        this.useYn = (useYn == null) ? this.useYn : useYn;
        this.sortOrder = (sortOrder == null) ? this.sortOrder : sortOrder;
    }

    /** 비활성화(목록에서 숨김). 상품이 쓰고 있어 삭제할 수 없을 때 쓴다. */
    public void deactivate() {
        this.useYn = false;
    }
}
