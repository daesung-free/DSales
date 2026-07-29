package com.daesung.sales.consignment.repository;

import com.daesung.sales.consignment.entity.ConsignmentSettlement;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsignmentSettlementRepository extends JpaRepository<ConsignmentSettlement, Long> {
    List<ConsignmentSettlement> findByConsignmentOutId(Long consignmentOutId);

    /**
     * 정산내역서: 기간 내 위탁 부분정산 이력 + 미결원장 현황 + 연결 매출 금액. 정산일 오름차순.
     * 반환 Object[]: [settledDate, partnerName, code, name, sourceOutNo, settleQty, salesRefNo,
     *   supplyAmount, tax, totalAmount, totalQty, settledQtyCum, remainingQty, status].
     */
    @Query(value = """
            SELECT DATE(cs.settled_at) AS settled_date, pt.name, p.code, p.name, co.source_out_no,
              cs.settle_qty, cs.sales_ref_no,
              COALESCE(s.supply_amount,0), COALESCE(s.tax,0), COALESCE(s.total_amount,0),
              co.total_qty, co.settled_qty, co.remaining_qty, co.status
            FROM consignment_settlement cs
              JOIN consignment_out co ON co.id = cs.consignment_out_id
              JOIN partners pt ON pt.id = co.partner_id
              JOIN products p ON p.id = co.product_id
              LEFT JOIN sales s ON s.sales_no = cs.sales_ref_no
            WHERE DATE(cs.settled_at) BETWEEN :fromDate AND :toDate
            ORDER BY cs.settled_at, cs.id
            """, nativeQuery = true)
    List<Object[]> settlementStatement(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
