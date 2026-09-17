package com.daesung.sales.inventory.entity;

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
 * 전표 취소 이력(폐기·입고). 근거: 발주처 회신 「삭제권한」 —
 * "마감 확정 전에는 잘못 등록한 건을 삭제할 수 있어야. 확정 후에는 물리 삭제 없이 취소 처리".
 *
 * <p>★<b>물리 삭제하지 않는다.</b> 재고는 {@code inventory_txn}(이벤트 로그)이 유일 진실이라
 * 지우면 "언제 왜 되돌렸나"가 사라진다. 반대 부호 이벤트를 새로 적어 상쇄한다.
 *
 * <p>★<b>왜 별도 표인가</b> — 폐기·입고는 머리 테이블이 없다. 매출은 {@code sales.canceled}를
 * 쓸 수 있었지만 이 둘은 {@code inventory_txn}에만 남아, 전표 단위 취소 여부를 적을 자리가 없다.
 */
@Entity
@Table(name = "voucher_cancel")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoucherCancel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ref_no", nullable = false, length = 30)
    private String refNo;

    @Column(name = "voucher_kind", nullable = false, length = 20)
    private String voucherKind;

    @Column(length = 500)
    private String reason;

    /** 되돌린 재고 이벤트 수. 0이면 재고 미관리 상품뿐이었다는 뜻 — 오류가 아니다. */
    @Column(nullable = false)
    private int reversed;

    public static VoucherCancel of(String refNo, String voucherKind, String reason, int reversed) {
        VoucherCancel v = new VoucherCancel();
        v.refNo = refNo;
        v.voucherKind = voucherKind;
        v.reason = reason;
        v.reversed = reversed;
        return v;
    }
}
