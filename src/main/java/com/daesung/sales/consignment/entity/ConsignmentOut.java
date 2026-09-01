package com.daesung.sales.consignment.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.inventory.entity.InventoryTxn;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.product.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 위탁 미결원장. 🆕 근거: 로직B (BE-30~35).
 * 불변식: total_qty = settled_qty + remaining_qty (DB CHECK로도 강제).
 */
@Entity
@Table(name = "consignment_out")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsignmentOut extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_out_no", nullable = false, unique = true, length = 30)
    private String sourceOutNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    /**
     * 아직 살아 있는 출고량. 불변식 {@code total = settled + remaining}을 지키려고
     * 반품하면 함께 줄어든다 — "처음 얼마 나갔나"는 {@link #originalQty}가 갖는다.
     */
    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    /** 처음 출고한 수량(불변). 반품해도 줄지 않는다. 원출고 = 정산 + 반품 + 미결잔여. */
    @Column(name = "original_qty", nullable = false)
    private int originalQty;

    /** 반품 누적. 이게 없으면 반품 뒤에 "처음부터 그만큼만 나간 것"처럼 보인다. */
    @Column(name = "returned_qty", nullable = false)
    private int returnedQty;

    @Column(name = "settled_qty", nullable = false)
    private int settledQty;

    @Column(name = "remaining_qty", nullable = false)
    private int remainingQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConsignmentStatus status = ConsignmentStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_txn_id")
    private InventoryTxn originTxn;

    public static ConsignmentOut create(String sourceOutNo, Product product, Partner partner,
                                        int totalQty, InventoryTxn originTxn) {
        ConsignmentOut c = new ConsignmentOut();
        c.sourceOutNo = sourceOutNo;
        c.product = product;
        c.partner = partner;
        c.totalQty = totalQty;
        c.originalQty = totalQty;
        c.returnedQty = 0;
        c.settledQty = 0;
        c.remainingQty = totalQty;
        c.status = ConsignmentStatus.OPEN;
        c.originTxn = originTxn;
        return c;
    }

    /** 정산 역산(위탁정산 매출 취소 시). settled −, remaining + — 미결 잔여 복원. 불변식 유지. */
    public void unsettle(int qty) {
        this.settledQty -= qty;
        this.remainingQty += qty;
        this.status = (this.settledQty > 0) ? ConsignmentStatus.PARTIAL : ConsignmentStatus.OPEN;
    }

    /**
     * 미정산분 반품(위탁 반품). 미결 잔여(미판매분)를 위탁창고→물류창고로 되돌림.
     * total_qty·remaining_qty 동시 차감(불변식 total=settled+remaining 유지). 매출 무관.
     */
    public void returnUnsold(int qty) {
        // ★초과 차단 제거(발주처 2026-08-31). 초과분은 호출부가 Case1으로 갈라 넘긴다 —
        //   여기까지 오는 qty는 이미 잔여 이하로 잘려 있어야 한다.
        this.totalQty -= qty;
        this.remainingQty -= qty;
        this.returnedQty += qty;   // 원출고 = 정산 + 반품 + 미결잔여 가 읽히도록 누적
        if (this.remainingQty == 0) {
            this.status = ConsignmentStatus.CLOSED;
        } else if (this.settledQty > 0) {
            this.status = ConsignmentStatus.PARTIAL;
        } else {
            this.status = ConsignmentStatus.OPEN;
        }
    }

    /**
     * 부분 정산. 불변식 total = settled + remaining 유지.
     *
     * <p>★<b>초과정산을 막지 않는다</b>(발주처 2026-08-31: "자동 차단하던 기존 로직은 제거,
     * 초과 시 경고 알림만"). 잔여를 넘겨 정산하면 {@code remainingQty}가 <b>음수</b>가 되고,
     * 그 사실이 그대로 화면에 보인다 — 담당자가 보고 수기로 정리하라는 것이 요구다.
     * 조용히 0으로 깎으면 초과했다는 사실 자체가 사라진다.
     */
    public void settle(int qty) {
        this.settledQty += qty;
        this.remainingQty -= qty;
        this.status = (this.remainingQty <= 0) ? ConsignmentStatus.CLOSED : ConsignmentStatus.PARTIAL;
    }

    /**
     * <b>확정매출분 반품(Case 1)</b>. 근거: 발주처 화면검토 확인요청서(2026-08-31) —
     * "100부 위탁출고 중 60부 정산 확정(미결잔여 40부) 상태에서 반품 50부가 등록되면,
     * 40부는 Case2로, <b>초과분 10부는 Case1</b>(기존 정산 확정분에 대한 반품,
     * 정산 수량을 50부로 정정 + 매출 마이너스 반영)으로 처리".
     *
     * <p>★<b>기존 정산 건을 소급 수정하지 않는다.</b> 원문: "정산이 여러 건에 나뉘어
     * 확정되어 있었더라도 특정 정산 건을 소급 수정하지 않고, 초과분은 별도 반품 건을
     * 등록하는 방식으로 처리". 그래서 {@code ConsignmentSettlement} 행은 건드리지 않고
     * 누적값만 되돌리며, 매출 반품 라인은 호출부가 따로 만든다.
     *
     * <p>잔여는 건드리지 않는다 — 이미 팔린 몫이 돌아온 것이라 미판매 잔여와 무관하다.
     * 결과적으로 <b>원출고 = 정산 + 반품 + 잔여</b>가 계속 읽힌다.
     */
    public void returnSettled(int qty) {
        this.settledQty -= qty;
        this.totalQty -= qty;
        this.returnedQty += qty;
        if (this.remainingQty <= 0) {
            this.status = ConsignmentStatus.CLOSED;
        } else if (this.settledQty > 0) {
            this.status = ConsignmentStatus.PARTIAL;
        } else {
            this.status = ConsignmentStatus.OPEN;
        }
    }
}
