package com.daesung.sales.logistics.entity;

import com.daesung.sales.common.entity.SoftDeletableEntity;
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
import org.hibernate.annotations.SQLRestriction;

/**
 * 매출프로그램 상품의 물류 단가(36p 신규등록). DSRE 시행 단가({@code tbl_logis_cost})와 <b>별개 축</b>이다.
 *
 * <p>DSRE 쪽은 {@code DTL_CD}(시행일코드)로 키를 잡는데 우리 상품(교재 등)엔 시행코드가 없고,
 * DSRE2는 분리 유지라 우리가 시행코드를 발급할 수도 없다. 발주처 회신이 그 답을 정해 뒀다 —
 * "Y(매출프로그램 단독 관리 — 물류비용등록 단가 자체 관리) / N(DSRE 병행 — DSRE 기준 유지)".
 *
 * <p>★<b>작업구분 기준단가를 복사해 둔다(참조가 아니다).</b> 참조로 두면 기준단가를 고치는 순간
 * 과거 출고의 작업비까지 따라 바뀐다 — 발주처 §1-1 "저장된 출고 작업비는 단가 변경에
 * 소급되지 않아야 함"에 정면으로 어긋난다.
 */
@Entity
@Table(name = "product_logis_rate")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductLogisRate extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "pack_type", nullable = false)
    private int packType;

    @Column(nullable = false)
    private int paper;

    @Column(nullable = false)
    private int omr;

    @Column(nullable = false)
    private int etc;

    @Column(nullable = false)
    private int label;

    @Column(nullable = false)
    private int basic;

    @Column(nullable = false)
    private int trade;

    // ‼️컬럼이 CHAR(1)이라 columnDefinition 을 명시한다 — 없으면 Hibernate가 VARCHAR(1)로 보고
    //   스키마 검증에서 기동이 막힌다(DSRE tbl_logis_cost.bSpare 와 같은 CHAR 계열로 맞춘 것).
    @Column(name = "b_spare", nullable = false, length = 1, columnDefinition = "char(1)")
    private String bSpare = "Y";

    /**
     * 개별 수정됨 — 일괄반영이 이 행을 건너뛴다.
     *
     * <p>담당자가 일부러 다른 값을 넣은 행이 일괄반영 한 번에 조용히 덮이면
     * 그 상품이 잘못된 단가로 청구된다(DSRE 쪽 {@code logis_rate_override}와 같은 역할).
     */
    @Column(nullable = false)
    private boolean overridden = false;

    /** 신규등록 — 작업구분 기준단가를 그대로 복사해 온다. */
    public static ProductLogisRate of(Product product, WorkType workType, String bSpare) {
        ProductLogisRate r = new ProductLogisRate();
        r.product = product;
        r.packType = workType.getPackType();
        r.applyFrom(workType);
        r.bSpare = (bSpare == null || bSpare.isBlank()) ? "Y" : bSpare;
        r.overridden = false;
        return r;
    }

    /** 작업구분 기준단가를 덮어쓴다(일괄반영). 개별 수정 표시는 건드리지 않는다. */
    public void applyFrom(WorkType w) {
        this.packType = w.getPackType();
        this.paper = w.getPaper();
        this.omr = w.getOmr();
        this.etc = w.getEtc();
        this.label = w.getLabel();
        this.basic = w.getBasic();
        this.trade = w.getTrade();
    }

    /**
     * 개별 수정. 지정하지 않은 항목은 건드리지 않는다 —
     * 라벨만 고치려다 기본작업비가 0으로 덮이면 그 상품 물류비가 통째로 틀어진다.
     *
     * <p>이 경로로 고친 행은 <b>예외로 표시</b>되어 이후 일괄반영이 건너뛴다.
     */
    public void update(Integer paper, Integer omr, Integer etc, Integer label,
                       Integer basic, Integer trade, String bSpare) {
        if (paper != null) {
            this.paper = paper;
        }
        if (omr != null) {
            this.omr = omr;
        }
        if (etc != null) {
            this.etc = etc;
        }
        if (label != null) {
            this.label = label;
        }
        if (basic != null) {
            this.basic = basic;
        }
        if (trade != null) {
            this.trade = trade;
        }
        if (bSpare != null && !bSpare.isBlank()) {
            this.bSpare = bSpare;
        }
        this.overridden = true;
    }

    /** 작업구분 변경 — 새 작업구분의 기준단가로 다시 채우고 예외 표시를 푼다. */
    public void changeWorkType(WorkType w) {
        applyFrom(w);
        this.overridden = false;
    }
}
