package com.daesung.sales.sale.repository;

import com.daesung.sales.sale.entity.Sale;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    /** 매출번호(I) 채번용 시퀀스. 레거시 Max+1(동시성 없음) 대체. */
    @Query(value = "SELECT nextval('seq_invoice_no')", nativeQuery = true)
    long nextInvoiceSeq();

    /** 통합 매출 조회(기간·회계구분·출고유형·거래처·취소포함 여부 필터). null이면 미적용. */
    @Query("select s from Sale s "
            + "where (:from is null or s.salesDate >= :from) "
            + "and (:to is null or s.salesDate <= :to) "
            + "and (:salesCategory is null or s.salesCategory = :salesCategory) "
            + "and (:shipmentType is null or s.shipmentType = :shipmentType) "
            + "and (:partnerId is null or s.partner.id = :partnerId) "
            + "and (:includeCanceled = true or s.canceled = false)")
    Page<Sale> search(@Param("from") LocalDate from,
                      @Param("to") LocalDate to,
                      @Param("salesCategory") SalesCategory salesCategory,
                      @Param("shipmentType") ShipmentType shipmentType,
                      @Param("partnerId") Long partnerId,
                      @Param("includeCanceled") boolean includeCanceled,
                      Pageable pageable);

    /**
     * 순매출 집계(상품별). 취소 제외. 회계구분·출고유형으로 매출/증정/교사용/반품 버킷 분해.
     * 순매출 = 매출 − 반품(수량·금액)은 서비스에서 계산. 근거: 레거시 순매출조회.
     * 반환 Object[]: [productId, code, name, saleQty, saleAmt, freeQty, freeAmt,
     *   teacherQty, teacherAmt, returnQty, returnAmt, tax, total]
     */
    @Query(value = """
            SELECT s.product_id, p.code, p.name,
              COALESCE(SUM(CASE WHEN s.sales_category='SALE' THEN s.qty ELSE 0 END),0) AS sale_qty,
              COALESCE(SUM(CASE WHEN s.sales_category='SALE' THEN s.supply_amount ELSE 0 END),0) AS sale_amt,
              COALESCE(SUM(CASE WHEN s.sales_category='FREE' AND s.shipment_type='GIFT' THEN s.qty ELSE 0 END),0) AS free_qty,
              COALESCE(SUM(CASE WHEN s.sales_category='FREE' AND s.shipment_type='GIFT' THEN s.supply_amount ELSE 0 END),0) AS free_amt,
              COALESCE(SUM(CASE WHEN s.sales_category='FREE' AND s.shipment_type='TEACHER_USE' THEN s.qty ELSE 0 END),0) AS teacher_qty,
              COALESCE(SUM(CASE WHEN s.sales_category='FREE' AND s.shipment_type='TEACHER_USE' THEN s.supply_amount ELSE 0 END),0) AS teacher_amt,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN s.qty ELSE 0 END),0) AS return_qty,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN s.supply_amount ELSE 0 END),0) AS return_amt,
              COALESCE(SUM(s.tax),0) AS tax,
              COALESCE(SUM(s.total_amount),0) AS total
            FROM sales s JOIN products p ON p.id = s.product_id
            WHERE s.canceled = false
              AND s.sales_date BETWEEN :fromDate AND :toDate
              AND (CAST(:partnerId AS bigint) IS NULL OR s.partner_id = :partnerId)
            GROUP BY s.product_id, p.code, p.name
            ORDER BY p.code
            """, nativeQuery = true)
    List<Object[]> salesSummary(@Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate,
                                @Param("partnerId") Long partnerId);
}
