package com.daesung.sales.sale.repository;

import com.daesung.sales.sale.dto.SalesStatementAgg;
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

    /**
     * 매출액명세서 도서 단위 집계(취소 제외, 기간·회계구분 필터). 근거: 레거시 매출액명세서.vb.
     * 금액=Σ공급가, 세액=Σ세액. 합계(금액+세액)와 대분류·소계·총계 rollup은 서비스에서 조립.
     * 정렬은 rollup 조립 위해 catCode·code 오름차순.
     */
    @Query("""
            select p.catCode as catCode, p.catName as catName,
                   p.code as bookCode, p.name as bookName,
                   sum(s.qty) as qty,
                   sum(coalesce(s.supplyAmount, 0)) as amount,
                   sum(coalesce(s.tax, 0)) as tax
            from Sale s join s.product p
            where s.canceled = false
              and s.salesDate between :from and :to
              and (:category is null or s.salesCategory = :category)
            group by p.catCode, p.catName, p.code, p.name
            order by p.catCode asc, p.code asc
            """)
    List<SalesStatementAgg> statementAgg(@Param("from") LocalDate from,
                                         @Param("to") LocalDate to,
                                         @Param("category") SalesCategory category);

    /** 매출일괄등록 멱등: 이미 등록된 소스키인지. */
    boolean existsByBulkImportKey(String bulkImportKey);

    /**
     * 월별 실적(순매출액 = 매출−반품). 취소 제외. 상품 필터(null=전체). 대시보드 목표대비용.
     * 반환 Object[]: [month(1~12), netAmount].
     */
    @Query(value = """
            SELECT EXTRACT(MONTH FROM s.sales_date) AS mon,
              COALESCE(SUM(CASE WHEN s.sales_category='SALE' THEN s.supply_amount
                                WHEN s.sales_category='RETURN' THEN -s.supply_amount ELSE 0 END),0) AS net_amt
            FROM sales s
            WHERE s.canceled = false
              AND EXTRACT(YEAR FROM s.sales_date) = :year
              AND (CAST(:productId AS bigint) IS NULL OR s.product_id = :productId)
            GROUP BY EXTRACT(MONTH FROM s.sales_date)
            """, nativeQuery = true)
    List<Object[]> monthlyNetSales(@Param("year") int year, @Param("productId") Long productId);

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

    /**
     * 거래처별 채권 발생액(기간). 취소 제외. 반품은 채권 감소(−total).
     * receivableGen = Σ(RETURN이면 −total_amount, else +total_amount) = 매출+세액+유가교사용 − 반품.
     * 반환 Object[]: [partnerId, receivableGen, saleAmt, returnAmt, tax].
     */
    @Query(value = """
            SELECT s.partner_id,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN -s.total_amount ELSE s.total_amount END),0) AS receivable_gen,
              COALESCE(SUM(CASE WHEN s.sales_category='SALE' THEN s.supply_amount ELSE 0 END),0) AS sale_amt,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN s.supply_amount ELSE 0 END),0) AS return_amt,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN -s.tax ELSE s.tax END),0) AS tax_net
            FROM sales s
            WHERE s.canceled = false
              AND s.sales_date BETWEEN :fromDate AND :toDate
              AND (CAST(:partnerId AS bigint) IS NULL OR s.partner_id = :partnerId)
            GROUP BY s.partner_id
            """, nativeQuery = true)
    List<Object[]> receivableByPartner(@Param("fromDate") LocalDate fromDate,
                                       @Param("toDate") LocalDate toDate,
                                       @Param("partnerId") Long partnerId);

    /**
     * 콘텐츠구분 순매출: 상품별 매출/무상/반품 집계 + 콘텐츠구분. 취소 제외.
     * 외부콘텐츠 이익은 서비스에서 매입원가(입고 unit_cost 평균)와 결합.
     * contentType: null=전체, 'SELF'/'EXTERNAL'.
     * 반환 Object[]: [productId, code, name, contentType, saleQty, saleAmt, freeAmt, returnQty, returnAmt].
     */
    @Query(value = """
            SELECT s.product_id, p.code, p.name, p.content_type,
              COALESCE(SUM(CASE WHEN s.sales_category='SALE' THEN s.qty ELSE 0 END),0) sale_qty,
              COALESCE(SUM(CASE WHEN s.sales_category='SALE' THEN s.supply_amount ELSE 0 END),0) sale_amt,
              COALESCE(SUM(CASE WHEN s.sales_category='FREE' THEN s.supply_amount ELSE 0 END),0) free_amt,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN s.qty ELSE 0 END),0) return_qty,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN s.supply_amount ELSE 0 END),0) return_amt
            FROM sales s JOIN products p ON p.id = s.product_id
            WHERE s.canceled = false
              AND s.sales_date BETWEEN :fromDate AND :toDate
              AND (CAST(:contentType AS varchar) IS NULL OR p.content_type = :contentType)
            GROUP BY s.product_id, p.code, p.name, p.content_type
            ORDER BY p.code
            """, nativeQuery = true)
    List<Object[]> netSalesByProduct(@Param("fromDate") LocalDate fromDate,
                                     @Param("toDate") LocalDate toDate,
                                     @Param("contentType") String contentType);

    /** 외상매출장 명세: 특정 거래처의 기간 내 매출 라인(취소 제외, 일자순). */
    @Query("select s from Sale s where s.partner.id = :partnerId and s.canceled = false "
            + "and s.salesDate between :from and :to order by s.salesDate, s.id")
    List<Sale> findLedgerLines(@Param("partnerId") Long partnerId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    /**
     * 수익신고: 거래처×월 순매출/세액 집계. 취소 제외. 반품은 −(순매출·세액 감소).
     * taxFilter: 'ALL'=전체, 'FREE'=면세(tax=0), 'TAXABLE'=과세(tax≠0).
     * 반환 Object[]: [partnerId, partnerName, yyyymm, count, netSupply, netTax].
     */
    @Query(value = """
            SELECT s.partner_id, pt.name, TO_CHAR(s.sales_date,'YYYYMM') AS ym,
              COUNT(*) AS cnt,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN -s.supply_amount ELSE s.supply_amount END),0) AS net_supply,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN -s.tax ELSE s.tax END),0) AS net_tax
            FROM sales s JOIN partners pt ON pt.id = s.partner_id
            WHERE s.canceled = false
              AND s.sales_date BETWEEN :fromDate AND :toDate
              AND ( :taxFilter = 'ALL'
                    OR (:taxFilter = 'FREE' AND s.tax = 0)
                    OR (:taxFilter = 'TAXABLE' AND s.tax <> 0) )
            GROUP BY s.partner_id, pt.name, TO_CHAR(s.sales_date,'YYYYMM')
            ORDER BY pt.name, ym
            """, nativeQuery = true)
    List<Object[]> revenueReport(@Param("fromDate") LocalDate fromDate,
                                 @Param("toDate") LocalDate toDate,
                                 @Param("taxFilter") String taxFilter);

    /**
     * 계산서신고: 거래처×상품×과세구분 집계(품목 라인). 취소 제외, 반품 차감.
     * tax_bucket: 'FREE'(면세, tax=0) / 'TAXABLE'(과세). 순매출 0인 품목은 제외.
     * 반환 Object[]: [partnerId, partnerName, productId, productName, taxBucket, supply, tax].
     */
    @Query(value = """
            SELECT s.partner_id, pt.name, s.product_id, p.name,
              CASE WHEN s.tax = 0 THEN 'FREE' ELSE 'TAXABLE' END AS tax_bucket,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN -s.supply_amount ELSE s.supply_amount END),0) AS supply,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN -s.tax ELSE s.tax END),0) AS tax
            FROM sales s
              JOIN partners pt ON pt.id = s.partner_id
              JOIN products p ON p.id = s.product_id
            WHERE s.canceled = false
              AND s.sales_date BETWEEN :fromDate AND :toDate
              AND (CAST(:partnerId AS bigint) IS NULL OR s.partner_id = :partnerId)
            GROUP BY s.partner_id, pt.name, s.product_id, p.name, CASE WHEN s.tax = 0 THEN 'FREE' ELSE 'TAXABLE' END
            HAVING COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN -s.supply_amount ELSE s.supply_amount END),0) <> 0
            ORDER BY pt.name, s.partner_id, tax_bucket, p.name
            """, nativeQuery = true)
    List<Object[]> taxInvoiceLines(@Param("fromDate") LocalDate fromDate,
                                   @Param("toDate") LocalDate toDate,
                                   @Param("partnerId") Long partnerId);
}
