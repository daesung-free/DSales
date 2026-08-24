package com.daesung.sales.logistics.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 작업구분 마스터(36p 물류비용등록). 근거: 발주처 회신 2026-08-21 —
 * "작업구분 자체도 고정값이 아니라 신규등록/수정/삭제 가능한 관리형 값으로 운영".
 *
 * <p>★DSRE2엔 이름을 적을 곳이 없다. {@code tbl_logis_cost.PACKTYPE}은 int 하나뿐이고
 * 뜻은 컬럼 주석('1:반별봉투, 2:개별봉투, 3:개별봉투(예외,2750SET)')에만 있다.
 * 4번·5번을 새로 만들면 그 숫자가 무엇인지 DSRE2는 모른다.
 * 그래서 <b>이름과 기준단가는 우리가 갖고, 단가 값은 DSRE2에 써넣는다</b>.
 */
@Entity
@Table(name = "work_type")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** DSRE2 {@code PACKTYPE} 값. 이 숫자로 단가 행과 이어진다 — 바꾸면 연결이 끊긴다. */
    @Column(name = "pack_type", nullable = false, unique = true)
    private int packType;

    @Column(nullable = false, length = 50)
    private String name;

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

    @Column(name = "use_yn", nullable = false)
    private boolean useYn = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static WorkType create(int packType, String name, int paper, int omr, int etc,
                                  int label, int basic, int trade, Boolean useYn, Integer sortOrder) {
        WorkType w = new WorkType();
        w.packType = packType;
        w.name = name;
        w.paper = paper;
        w.omr = omr;
        w.etc = etc;
        w.label = label;
        w.basic = basic;
        w.trade = trade;
        w.useYn = (useYn == null) || useYn;
        w.sortOrder = (sortOrder == null) ? 0 : sortOrder;
        return w;
    }

    /**
     * 수정. <b>packType은 바꾸지 않는다</b> — 그 숫자가 DSRE2 단가 행과 잇는 유일한 키라,
     * 바꾸면 이미 그 작업구분으로 등록된 상품들이 통째로 연결을 잃는다.
     *
     * <p>{@code useYn}은 null이면 기존 값을 유지한다(일부 필드만 보낸 수정이 조용히 끄지 않게).
     */
    public void update(String name, int paper, int omr, int etc, int label, int basic, int trade,
                       Boolean useYn, Integer sortOrder) {
        this.name = name;
        this.paper = paper;
        this.omr = omr;
        this.etc = etc;
        this.label = label;
        this.basic = basic;
        this.trade = trade;
        if (useYn != null) {
            this.useYn = useYn;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }

    public void deactivate() {
        this.useYn = false;
    }
}
