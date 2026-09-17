package com.daesung.sales.inventory.repository;

import com.daesung.sales.inventory.entity.VoucherCancel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherCancelRepository extends JpaRepository<VoucherCancel, Long> {

    /** 같은 전표를 두 번 취소하면 재고가 반대로 밀린다. 호출 전에 반드시 확인한다. */
    boolean existsByRefNo(String refNo);
}
