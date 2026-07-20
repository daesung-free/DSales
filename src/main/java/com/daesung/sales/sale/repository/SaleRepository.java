package com.daesung.sales.sale.repository;

import com.daesung.sales.sale.entity.Sale;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    List<Sale> findByPartnerId(Long partnerId);
    List<Sale> findBySalesDateBetween(LocalDate from, LocalDate to);
}
