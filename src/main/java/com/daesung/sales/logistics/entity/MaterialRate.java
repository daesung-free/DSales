package com.daesung.sales.logistics.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.product.entity.MaterialType;
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
 * 자재구분×작업구분 단가. 세트 조립 작업비 자동계산의 단가 소스.
 * 근거: 정본 구분값정리 10.물류비용등록(자재별 단가가 33p BOM '자재구분'과 매칭, 작업구분별로 상이).
 *
 * <p>{@code packType=0}은 작업구분 구분 없는 <b>공통 단가</b>다. 특정 작업구분 단가가 없으면
 * 공통 단가로 떨어진다 — 단가표를 작업구분마다 다 채우지 않아도 계산이 되도록.
 */
@Entity
@Table(name = "material_rate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaterialRate extends BaseEntity {

    /** 작업구분 미지정(공통 단가). */
    public static final int COMMON_PACK_TYPE = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 20)
    private MaterialType materialType;

    @Column(name = "pack_type", nullable = false)
    private int packType;

    /** 자재 1개당 단가(원). */
    @Column(name = "unit_rate", nullable = false)
    private int unitRate;

    @Column(length = 200)
    private String memo;

    public static MaterialRate of(MaterialType materialType, Integer packType, int unitRate, String memo) {
        MaterialRate r = new MaterialRate();
        r.materialType = materialType;
        r.packType = (packType == null) ? COMMON_PACK_TYPE : packType;
        r.unitRate = unitRate;
        r.memo = memo;
        return r;
    }

    public void update(int unitRate, String memo) {
        this.unitRate = unitRate;
        this.memo = memo;
    }
}
