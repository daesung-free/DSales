package com.daesung.sales.material.entity;

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

/**
 * 세트·회차 ↔ 자재 매칭 + 세트당 소요수량.
 * 근거: 발주처 구조보완요청안(2026-08-31) "구성회차별 매칭 결과" 표.
 *
 * <p>★{@code roundProduct == null}이 <b>"공통"</b>이다 — 세트 전체에 붙는 범용 자재
 * (OMR·교사용라벨·해설강의쿠폰). 회차별 전용 자재(시험지·해설지)는 회차를 지정한다.
 *
 * <p>★<b>소요수량은 "세트당" 최종 수량</b>이다. 원문:
 * "'공통'으로 매칭되는 범용 자재도 세트 내 회차 반복 여부를 고려한 최종 수량으로 입력해야
 * 합니다 — 회차마다 반복 사용되는 자재(OMR 등)는 세트 내 회차 수만큼 반영한 값을,
 * 세트 전체에 한 번만 필요한 자재는 1로 고정한 값을 입력합니다."
 * 즉 4회차 구성이면 OMR은 4, 해설강의쿠폰은 1이다 — <b>서버가 회차 수를 곱하지 않는다.</b>
 */
@Entity
@Table(name = "material_bom")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaterialBom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "set_product_id", nullable = false)
    private Product setProduct;

    /** 회차 상품. null이면 공통(세트 전체). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_product_id")
    private Product roundProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "qty_per_set", nullable = false)
    private int qtyPerSet;

    /**
     * <b>회차 반복형</b> 여부(공통 자재 전용). 근거: 구조보완요청안 각주 —
     * "회차마다 반복 사용되는 자재(예: OMR, 4회차 구성 기준 4)는 회차 수만큼 반영한 값을,
     * 세트 전체에 한 번만 필요한 자재(예: 해설강의쿠폰)는 1로 고정한 값을 입력합니다."
     *
     * <p>문서가 공통 자재를 두 종류로 나눠 놓았는데 숫자만으로는 구분되지 않는다 —
     * {@code 4}가 "4회차 × 1"인지 "세트당 4개 고정"인지 알 수 없다. 이 플래그가 그 구분이다.
     * 회차 전용 자재({@code roundProduct != null})에는 의미가 없다.
     */
    @Column(name = "per_round", nullable = false)
    private boolean perRound;

    public static MaterialBom of(Product setProduct, Product roundProduct,
                                 Material material, int qtyPerSet, Boolean perRound) {
        MaterialBom b = new MaterialBom();
        b.setProduct = setProduct;
        b.roundProduct = roundProduct;
        b.material = material;
        b.qtyPerSet = qtyPerSet;
        // 회차 전용 자재는 애초에 회차에 붙어 있어 '반복' 개념이 없다 — 공통일 때만 켠다.
        b.perRound = (roundProduct == null) && Boolean.TRUE.equals(perRound);
        return b;
    }

    public void updateQty(int qtyPerSet, Boolean perRound) {
        this.qtyPerSet = qtyPerSet;
        if (perRound != null) {
            this.perRound = isCommon() && perRound;
        }
    }

    /** 공통 매칭 여부(회차 미지정). */
    public boolean isCommon() {
        return roundProduct == null;
    }

    /**
     * 회차 하나당 소요수량.
     *
     * <p>회차 전용 자재는 입력값 그대로. <b>공통 반복형</b>은 세트당 수량이 이미
     * "회차 수만큼 반영한 값"이므로 <b>회차 수로 나눠야</b> 회차당 수량이 나온다(OMR 4 ÷ 4회차 = 1).
     * 공통 1회형은 회차 단독으로는 나가지 않으므로 0이다 — 세트를 사야 붙는 자재다.
     */
    public long qtyPerRound(int roundCount) {
        if (!isCommon()) {
            return qtyPerSet;
        }
        if (!perRound || roundCount <= 0) {
            return 0L;
        }
        return (long) qtyPerSet / roundCount;
    }
}
