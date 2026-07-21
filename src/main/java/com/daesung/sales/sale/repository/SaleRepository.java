package com.daesung.sales.sale.repository;

import com.daesung.sales.sale.entity.Sale;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    List<Sale> findByPartnerId(Long partnerId);
    List<Sale> findBySalesDateBetween(LocalDate from, LocalDate to);

    /** 매출번호(I) 채번용 시퀀스. 레거시 Max+1(동시성 없음) 대체. */
    @Query(value = "SELECT nextval('seq_invoice_no')", nativeQuery = true)
    long nextInvoiceSeq();
}
