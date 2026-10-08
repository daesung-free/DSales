package com.daesung.sales.closing.repository;

import com.daesung.sales.closing.entity.TaxInvoiceAdjustment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxInvoiceAdjustmentRepository extends JpaRepository<TaxInvoiceAdjustment, Long> {

    Optional<TaxInvoiceAdjustment> findByIssueDateAndPartnerIdAndTaxBucket(
            LocalDate issueDate, Long partnerId, String taxBucket);

    /** 그 작성일자의 보정 전체. 조회 한 번에 맵으로 올려 쓴다(행마다 조회하면 N+1이다). */
    List<TaxInvoiceAdjustment> findByIssueDate(LocalDate issueDate);
}
