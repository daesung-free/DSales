package com.daesung.sales.sale.repository;

import com.daesung.sales.sale.entity.Sale;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import java.time.LocalDate;
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
}
