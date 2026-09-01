package com.daesung.sales.material.entity;

import com.daesung.sales.common.entity.SoftDeletableEntity;
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
import org.hibernate.annotations.SQLRestriction;

/**
 * 자재 마스터. 근거: 발주처 「도서관리·제품수불부현황 데이터 구조 보완 요청안」(2026-08-31).
 *
 * <p>원문: "자재를 세트별 BOM에 매번 새로 입력하는 방식이 아니라, <b>별도 자재 목록에서
 * 등록·관리하고 이를 각 세트의 구성회차에 선택해 매칭</b>하는 방식으로 반영 부탁드립니다."
 *
 * <p>★<b>상품(products)이 아니다.</b> 자재는 팔지 않아 정가·공급률·매출구분이 무의미하고,
 * 무엇보다 범용 자재(OMR·교사용라벨)는 <b>1건을 여러 세트·회차에 중복 매칭</b>해야 하는데
 * 상품 BOM은 (부모, 자식) 유일 제약이라 세트마다 행을 새로 만들어야 한다.
 *
 * <p>자재명은 콘텐츠를 특정할 수 있게 등록한다(예: {@code 2026_D.ARCHIVE 국어 시즌1_01회}).
 * 시험지는 회차마다 전용 자재로 등록되고, 해설지도 보통 회차별이나 상품에 따라
 * 여러 회차 분을 하나로 묶기도 한다. OMR·라벨만 여러 세트에 공통으로 재사용된다.
 */
@Entity
@Table(name = "materials")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Material extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    /** ★물류 작업비 단가를 고르는 키. 등록 시 정확히 선택하는 것이 중요하다(발주처 강조). */
    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 20)
    private MaterialType materialType;

    @Column(name = "use_yn", nullable = false)
    private boolean useYn = true;

    @Column(length = 200)
    private String memo;

    public static Material create(String code, String name, MaterialType type, String memo) {
        Material m = new Material();
        m.code = code;
        m.name = name;
        m.materialType = type;
        m.useYn = true;
        m.memo = memo;
        return m;
    }

    /** 수정. 코드는 바꾸지 않는다 — BOM 매칭이 참조하는 식별자다. */
    public void update(String name, MaterialType type, Boolean useYn, String memo) {
        if (name != null) {
            this.name = name;
        }
        if (type != null) {
            this.materialType = type;
        }
        if (useYn != null) {
            this.useYn = useYn;
        }
        this.memo = memo;
    }
}
