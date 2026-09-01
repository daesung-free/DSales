package com.daesung.sales.product.entity;

import com.daesung.sales.common.entity.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
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

    /** 세트당 소요수량(33p). BOM 가변비율. */
    @Column(nullable = false)
    private int ratio;

    /** 구성회차(0=회차 구분 없음). 같은 자재가 회차별로 반복 등록될 수 있어 유니크 키에 포함된다. */
    @Column(nullable = false)
    private int round;

    /** 시행예정일(회차별). */
    @Column(name = "exam_date")
    private LocalDate examDate;

    /** 분리포장여부. */
    @Column(name = "separate_pack", nullable = false)
    private boolean separatePack;

    /** 자재구분 — 물류비용등록 작업구분과 1:1 대응(33p·36p). */
    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", length = 20)
    private MaterialType materialType;

    /** 물류비용 연계 — 물류비용등록(36p) 작업구분(PACKTYPE). 3=개별봉투(SET). */

    public static BomItem create(Product parent, Product child, int ratio) {
        BomItem b = new BomItem();
        b.parent = parent;
        b.child = child;
        b.ratio = ratio;
        return b;
    }

    /** 33p 상세 필드 지정(선택 입력). 미지정 시 회차 0·분리포장 false로 남는다. */
    public BomItem applyDetail(Integer round, LocalDate examDate, Boolean separatePack,
                               MaterialType materialType) {
        this.round = (round == null) ? 0 : round;
        this.examDate = examDate;
        this.separatePack = separatePack != null && separatePack;
        this.materialType = materialType;
        return this;
    }
}
