package com.daesung.sales.sale.repository;

import com.daesung.sales.sale.dto.AttendanceAgg;
import com.daesung.sales.sale.dto.BookSalesAgg;
import com.daesung.sales.sale.dto.CategorySalesAgg;
import com.daesung.sales.sale.dto.PartnerProductSalesAgg;
import com.daesung.sales.sale.dto.ReturnableAgg;
import com.daesung.sales.sale.dto.ShipmentQtyAgg;
import com.daesung.sales.sale.dto.ShipmentWarehouseAgg;
import com.daesung.sales.sale.dto.WorkOrderLineAgg;
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

    /**
     * 매출액명세서 도서 단위 집계(취소 제외, 기간·회계구분 필터). 근거: 레거시 매출액명세서.vb.
     * 금액=Σ공급가, 세액=Σ세액. 합계(금액+세액)와 대분류·소계·총계 rollup은 서비스에서 조립.
     *
     * <p>★대분류는 세부구분 마스터를 <b>left join</b>해 가져온다(발주처 회신 2026-08-20).
     * left인 이유: 세부구분이 없거나 마스터에 없는 상품도 매출은 실재하므로 빠지면 안 된다
     * (그런 행은 대분류 null → 서비스에서 '미분류'로 모인다).
     *
     * <p>행 순서는 rollup 조립용으로 catCode·code 오름차순이고, <b>대분류 순서는 서비스에서 정한다</b> —
     * 대분류를 SQL로 정렬하면 enum 이름 알파벳순(ETC, ETC_EXAM, MOCK_EXAM…)이 되어
     * 화면 순서(모의고사·교재·기타고사·특강·기타)와 어긋난다.
     */
    @Query("""
            select d.majorCategory as majorCategory,
                   p.catCode as catCode, p.catName as catName,
                   p.code as bookCode, p.name as bookName,
                   sum(s.qty) as qty,
                   sum(coalesce(s.supplyAmount, 0)) as amount,
                   sum(coalesce(s.tax, 0)) as tax
            from Sale s join s.product p
              left join SalesDivision d on d.code = p.salesDivision
            where s.canceled = false
              and s.salesDate between :from and :to
              and (:category is null or s.salesCategory = :category)
            group by d.majorCategory, p.catCode, p.catName, p.code, p.name
            order by p.catCode asc, p.code asc
            """)
    List<SalesStatementAgg> statementAgg(@Param("from") LocalDate from,
                                         @Param("to") LocalDate to,
                                         @Param("category") SalesCategory category);

    /**
     * 거래명세서 라인(거래처×기간). 취소 제외, 지정 회계구분(들)만. 상품 fetch join으로 N+1 방지.
     * 정렬: 분류코드→도서코드→매출일자. 근거: 레거시 거래명세서 데이터셋.
     */
    @Query("select s from Sale s join fetch s.product p "
            + "where s.partner.id = :partnerId and s.canceled = false "
            + "and s.salesDate between :from and :to "
            + "and s.salesCategory in :categories "
            + "order by p.catCode asc, p.code asc, s.salesDate asc")
    List<Sale> statementLines(@Param("partnerId") Long partnerId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to,
                              @Param("categories") java.util.Collection<SalesCategory> categories);

    /**
     * 과목별매출현황 집계(거래처×분류×도서). 취소 제외, 거래처·분류 옵션 필터.
     * 매출/반품/교사용 수량을 salesCategory로 버킷. 근거: 레거시 과목별매출현황.vb.
     */
    @Query("""
            select pt.id as partnerId, pt.code as partnerCode, pt.name as partnerName,
                   p.catCode as catCode, p.catName as catName, p.code as bookCode, p.name as bookName,
                   sum(case when s.salesCategory = com.daesung.sales.salestype.entity.SalesCategory.SALE then s.qty else 0 end) as saleQty,
                   sum(case when s.salesCategory = com.daesung.sales.salestype.entity.SalesCategory.RETURN then s.qty else 0 end) as returnQty,
                   sum(case when s.salesCategory = com.daesung.sales.salestype.entity.SalesCategory.FREE then s.qty else 0 end) as teacherQty
            from Sale s join s.product p join s.partner pt
            where s.canceled = false
              and s.salesDate between :from and :to
              and (:partnerId is null or pt.id = :partnerId)
              and (:catCode is null or p.catCode = :catCode)
            group by pt.id, pt.code, pt.name, p.catCode, p.catName, p.code, p.name
            order by pt.code asc, p.catCode asc, p.code asc
            """)
    List<CategorySalesAgg> categorySales(@Param("from") LocalDate from,
                                         @Param("to") LocalDate to,
                                         @Param("partnerId") Long partnerId,
                                         @Param("catCode") String catCode);

    /**
     * 도서입출고현황의 매출측(상품별 출고/반품 수량·금액). 취소 제외.
     * 근거: 레거시 도서입출고현황.vb 매출/반품 버킷. 매입측·재고는 InventoryTxnRepository에서 병합.
     */
    @Query("select p.id as productId, "
            + "sum(case when s.salesCategory = com.daesung.sales.salestype.entity.SalesCategory.SALE then s.qty else 0 end) as outQty, "
            + "sum(case when s.salesCategory = com.daesung.sales.salestype.entity.SalesCategory.SALE then coalesce(s.supplyAmount,0) else 0 end) as outAmt, "
            + "sum(case when s.salesCategory = com.daesung.sales.salestype.entity.SalesCategory.RETURN then s.qty else 0 end) as retQty, "
            + "sum(case when s.salesCategory = com.daesung.sales.salestype.entity.SalesCategory.RETURN then coalesce(s.supplyAmount,0) else 0 end) as retAmt "
            + "from Sale s join s.product p "
            + "where s.canceled = false and s.salesDate between :from and :to "
            + "group by p.id")
    List<BookSalesAgg> bookSalesAgg(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 교재식 반품 가능내역: 거래처×도서×정가×공급률 단위로 누적 판매출고(SALE)·기반품(RETURN) 집계.
     * 반품가능수량 = SALE − RETURN(서비스에서 계산·필터). 취소 제외. productId 옵션 필터.
     * 공급률·정가별로 그룹 → 반품 시 원 출고건 공급률 고정 적용의 근거가 됨.
     */
    @Query("select p.id as productId, p.code as productCode, p.name as productName, "
            + "s.unitPrice as unitPrice, s.supplyRate as supplyRate, "
            + "sum(case when s.salesCategory = com.daesung.sales.salestype.entity.SalesCategory.SALE then s.qty else 0 end) as saleQty, "
            + "sum(case when s.salesCategory = com.daesung.sales.salestype.entity.SalesCategory.RETURN then s.qty else 0 end) as returnQty "
            + "from Sale s join s.product p "
            + "where s.partner.id = :partnerId and s.canceled = false "
            + "and (:productId is null or p.id = :productId) "
            + "group by p.id, p.code, p.name, s.unitPrice, s.supplyRate "
            + "order by p.code, s.supplyRate")
    List<ReturnableAgg> returnableAgg(@Param("partnerId") Long partnerId, @Param("productId") Long productId);

    /**
     * 거래처×상품 매출집계(SALE만, 취소 제외). 거래처별 매출대비표(전년 동기간 비교)용.
     * 근거: 레거시 거래처별_매출대비표.vb(매출+유상무상=우리 SALE로 정규화). 거래처·분류 옵션 필터.
     */
    @Query("select pt.id as partnerId, pt.code as partnerCode, pt.name as partnerName, "
            + "p.catCode as catCode, p.catName as catName, "
            + "p.id as productId, p.code as bookCode, p.name as bookName, "
            + "sum(s.qty) as qty, sum(coalesce(s.supplyAmount,0)) as amount "
            + "from Sale s join s.partner pt join s.product p "
            + "where s.canceled = false "
            + "and s.salesCategory = com.daesung.sales.salestype.entity.SalesCategory.SALE "
            + "and s.salesDate between :from and :to "
            + "and (:partnerId is null or pt.id = :partnerId) "
            + "and (:catCode is null or p.catCode = :catCode) "
            + "group by pt.id, pt.code, pt.name, p.catCode, p.catName, p.id, p.code, p.name")
    List<PartnerProductSalesAgg> salesByPartnerProduct(@Param("from") LocalDate from,
                                                       @Param("to") LocalDate to,
                                                       @Param("partnerId") Long partnerId,
                                                       @Param("catCode") String catCode);

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
              AND (CAST(:productId AS SIGNED) IS NULL OR s.product_id = :productId)
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
              AND (CAST(:partnerId AS SIGNED) IS NULL OR s.partner_id = :partnerId)
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
              AND (CAST(:partnerId AS SIGNED) IS NULL OR s.partner_id = :partnerId)
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
              AND (CAST(:contentType AS CHAR) IS NULL OR p.content_type = :contentType)
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
            SELECT s.partner_id, pt.name, DATE_FORMAT(s.sales_date,'%Y%m') AS ym,
              COUNT(*) AS cnt,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN -s.supply_amount ELSE s.supply_amount END),0) AS net_supply,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN -s.tax ELSE s.tax END),0) AS net_tax
            FROM sales s JOIN partners pt ON pt.id = s.partner_id
            WHERE s.canceled = false
              AND s.sales_date BETWEEN :fromDate AND :toDate
              AND ( :taxFilter = 'ALL'
                    OR (:taxFilter = 'FREE' AND s.tax = 0)
                    OR (:taxFilter = 'TAXABLE' AND s.tax <> 0) )
            GROUP BY s.partner_id, pt.name, DATE_FORMAT(s.sales_date,'%Y%m')
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
              AND (CAST(:partnerId AS SIGNED) IS NULL OR s.partner_id = :partnerId)
            GROUP BY s.partner_id, pt.name, s.product_id, p.name, CASE WHEN s.tax = 0 THEN 'FREE' ELSE 'TAXABLE' END
            HAVING COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN -s.supply_amount ELSE s.supply_amount END),0) <> 0
            ORDER BY pt.name, s.partner_id, tax_bucket, p.name
            """, nativeQuery = true)
    List<Object[]> taxInvoiceLines(@Param("fromDate") LocalDate fromDate,
                                   @Param("toDate") LocalDate toDate,
                                   @Param("partnerId") Long partnerId);

    /**
     * 계산서·세금계산서 월별신고(38p): 월×발행유형으로 매출/반품/세액 집계. 취소 제외.
     * 발행유형: tax=0 → 'INVOICE'(계산서/면세), tax≠0 → 'TAX_INVOICE'(세금계산서/과세).
     *   (기존 계산서신고 taxInvoiceLines와 동일 버킷 규칙.)
     * 무상(FREE)은 매출/반품에서 제외(SALE·RETURN만 집계).
     * 반환 Object[]: [month(1~12), issueType, saleSupply, returnSupply, netTax].
     */
    @Query(value = """
            SELECT EXTRACT(MONTH FROM s.sales_date) AS mon,
              CASE WHEN s.tax = 0 THEN 'INVOICE' ELSE 'TAX_INVOICE' END AS issue_type,
              COALESCE(SUM(CASE WHEN s.sales_category='SALE'   THEN s.supply_amount ELSE 0 END),0) AS sale_supply,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN s.supply_amount ELSE 0 END),0) AS return_supply,
              COALESCE(SUM(CASE WHEN s.sales_category='RETURN' THEN -s.tax ELSE s.tax END),0)      AS net_tax
            FROM sales s
            WHERE s.canceled = false
              AND EXTRACT(YEAR FROM s.sales_date) = :year
            GROUP BY EXTRACT(MONTH FROM s.sales_date),
                     CASE WHEN s.tax = 0 THEN 'INVOICE' ELSE 'TAX_INVOICE' END
            ORDER BY mon
            """, nativeQuery = true)
    List<Object[]> taxFilingByMonth(@Param("year") int year);

    /**
     * 계산서 10일 분기용: 특정 연·월의 반품(RETURN) 라인(취소 제외). 처리일 오름차순.
     * 반환 Object[]: [salesNo, partnerName, productName, salesDate, supplyAmount, tax].
     */
    @Query(value = """
            SELECT s.sales_no, pt.name, p.name, s.sales_date,
              COALESCE(s.supply_amount,0), COALESCE(s.tax,0)
            FROM sales s
              JOIN partners pt ON pt.id = s.partner_id
              JOIN products p  ON p.id = s.product_id
            WHERE s.canceled = false
              AND s.sales_category = 'RETURN'
              AND EXTRACT(YEAR FROM s.sales_date) = :year
              AND EXTRACT(MONTH FROM s.sales_date) = :month
            ORDER BY s.sales_date, s.sales_no
            """, nativeQuery = true)
    List<Object[]> returnsInMonth(@Param("year") int year, @Param("month") int month);

    /**
     * 월별매출액명세서(37p): 대분류×분류×상품별 성적처리/비처리 인원·금액 + 과세·부가세. 매출(SALE)만, 취소 제외.
     * 성적처리 = proc_type='GRADED', 그 외(비처리/미지정)는 UNGRADED 버킷. 인원=qty, 금액=supply_amount.
     * ★대분류 = 세부구분 마스터({@code sales_divisions.major_category})에서 파생. 발주처 회신 2026-08-20.
     * 예전엔 {@code LEFT(cat_code,1)}이라 화면에 'H'·'M' 같은 글자가 나왔고, 분류코드 체계가 없는
     * 신규 데이터에서는 대분류가 무의미했다. LEFT JOIN인 이유는 세부구분이 없는 상품의 매출도
     * 빠지면 안 되기 때문(대분류 null → 서비스에서 '미분류'로 모인다).
     * 대분류 순서는 서비스에서 정한다 — SQL로 정렬하면 enum 이름 알파벳순이라 화면 순서와 어긋난다.
     *
     * <p>반환 Object[]:
     *   [major, catCode, catName, productId, code, name, gradedQty, gradedAmt, ungradedQty, ungradedAmt, taxableAmt, vat].
     */
    @Query(value = """
            SELECT d.major_category AS major, p.cat_code, p.cat_name, s.product_id, p.code, p.name,
              COALESCE(SUM(CASE WHEN s.proc_type='GRADED' THEN s.qty ELSE 0 END),0)            AS graded_qty,
              COALESCE(SUM(CASE WHEN s.proc_type='GRADED' THEN s.supply_amount ELSE 0 END),0)  AS graded_amt,
              COALESCE(SUM(CASE WHEN s.proc_type='GRADED' THEN 0 ELSE s.qty END),0)            AS ungraded_qty,
              COALESCE(SUM(CASE WHEN s.proc_type='GRADED' THEN 0 ELSE s.supply_amount END),0)  AS ungraded_amt,
              COALESCE(SUM(CASE WHEN s.tax <> 0 THEN s.supply_amount ELSE 0 END),0)            AS taxable_amt,
              COALESCE(SUM(s.tax),0)                                                           AS vat
            FROM sales s JOIN products p ON p.id = s.product_id
              LEFT JOIN sales_divisions d ON d.code = p.sales_division
            WHERE s.canceled = false
              AND s.sales_category = 'SALE'
              AND EXTRACT(YEAR FROM s.sales_date) = :year
              AND EXTRACT(MONTH FROM s.sales_date) = :month
            GROUP BY d.major_category, p.cat_code, p.cat_name, s.product_id, p.code, p.name
            ORDER BY d.major_category, p.cat_code, p.code
            """, nativeQuery = true)
    List<Object[]> monthlyStatementAgg(@Param("year") int year, @Param("month") int month);

    /**
     * 회차별 작업현황(구 IC회차별작업현황). 분류×도서×회차 단위로 포장구분별 수량을 펼친다.
     * 근거: 레거시 IC회차별작업현황.vb — {@code group by catCode, bookCode, bookReqSeq, packType} 후
     * 포장구분을 열로 피벗한 구조를 한 번에 집계한다.
     * 레거시의 {@code bookReqSeq<>0} 조건(회차 없는 건 제외)을 그대로 옮겼다.
     */
    @Query(value = """
            SELECT p.cat_code, MAX(p.cat_name), p.code, MAX(p.name), s.book_round,
                   COALESCE(SUM(CASE WHEN s.pack_type = 'INDIVIDUAL_1' THEN s.qty END), 0) AS indiv1,
                   COALESCE(SUM(CASE WHEN s.pack_type = 'INDIVIDUAL_2' THEN s.qty END), 0) AS indiv2,
                   COALESCE(SUM(CASE WHEN s.pack_type = 'CLASS_BUNDLE' THEN s.qty END), 0) AS cls,
                   COALESCE(SUM(s.qty), 0) AS total
              FROM sales s JOIN products p ON p.id = s.product_id
             WHERE s.canceled = false
               AND s.sales_date BETWEEN :fromDate AND :toDate
               AND s.book_round IS NOT NULL AND s.book_round <> 0
               AND (:catCode IS NULL OR p.cat_code = :catCode)
             GROUP BY p.cat_code, p.code, s.book_round
             ORDER BY p.cat_code, p.code, s.book_round
            """, nativeQuery = true)
    List<Object[]> roundWorkStatus(@Param("fromDate") java.time.LocalDate fromDate,
                                   @Param("toDate") java.time.LocalDate toDate,
                                   @Param("catCode") String catCode);

    /**
     * 발송 단위(일자·거래처·학교)의 상품군별 수량. 작업결과 화면의 '교재'·'IC' 컬럼.
     * 근거: 작업결과.vb — 레거시도 sendData에 salesData를 조인해 tradeClass별로 합산한다.
     * 취소·반품은 제외한다(내보내는 작업이 아니다).
     */
    @Query("""
            select s.salesDate as tradeDate, s.partner.id as partnerId,
                   coalesce(s.schoolCode, '') as schoolCode,
                   coalesce(p.salesDivision, '') as tradeClass,
                   sum(s.qty) as qty
              from Sale s join s.product p
             where s.canceled = false
               and s.salesCategory <> com.daesung.sales.salestype.entity.SalesCategory.RETURN
               and s.salesDate between :from and :to
             group by s.salesDate, s.partner.id, coalesce(s.schoolCode, ''), coalesce(p.salesDivision, '')
            """)
    List<ShipmentQtyAgg> shipmentQty(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 발송 단위의 도서별 상세. 작업요청서가 "무엇을 몇 개 포장할지" 지시하는 목록이다.
     * 근거: 작업요청서.vb 조회 SQL(도서코드·도서명·수량·정가·공급률·금액 컬럼).
     */
    @Query("""
            select s.salesDate as tradeDate, s.partner.id as partnerId,
                   coalesce(s.schoolCode, '') as schoolCode,
                   coalesce(p.salesDivision, '') as tradeClass,
                   p.catCode as catCode, p.code as productCode, p.name as productName,
                   s.bookRound as bookRound,
                   s.unitPrice as unitPrice, s.supplyRate as supplyRate,
                   sum(s.qty) as qty, sum(s.supplyAmount) as amount
              from Sale s join s.product p
             where s.canceled = false
               and s.salesCategory <> com.daesung.sales.salestype.entity.SalesCategory.RETURN
               and s.salesDate between :from and :to
             group by s.salesDate, s.partner.id, coalesce(s.schoolCode, ''), coalesce(p.salesDivision, ''),
                      p.catCode, p.code, p.name, s.bookRound, s.unitPrice, s.supplyRate
             order by p.catCode, p.code
            """)
    List<WorkOrderLineAgg> workOrderLines(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 응시현황(연도별) 원자료 — 거래처×월 수량·금액. 근거: 레거시 응시현황.vb:330~.
     *
     * <p>레거시는 {@code salesData}를 학교(schCode)로 묶어 월별 bookCnt/totalAmt를 만들고
     * 거래처로 rollup한다. 반품은 레거시가 <b>음수로 저장</b>해 SUM만으로 순수량이 나오는데,
     * 우리는 RETURN을 양수+구분으로 저장하므로 <b>여기서 부호를 뒤집어</b> 같은 값이 되게 한다.
     * 취소 건은 양쪽 모두 제외한다.
     *
     * <p>필터(학년·상품구분)는 레거시 queryWhere와 같은 축이다(bookData.grade / bookData.type).
     */
    @Query("""
            select p.id as partnerId, p.code as partnerCode, p.name as partnerName,
                   p.cityName as cityName,
                   month(s.salesDate) as month,
                   sum(case when s.salesCategory = com.daesung.sales.salestype.entity.SalesCategory.RETURN
                            then -s.qty else s.qty end) as qty,
                   sum(case when s.salesCategory = com.daesung.sales.salestype.entity.SalesCategory.RETURN
                            then -s.totalAmount else s.totalAmount end) as amount
              from Sale s join s.partner p join s.product b
             where s.canceled = false
               and year(s.salesDate) = :year
               and (:grade is null or b.grade = :grade)
               and (:productType is null or b.productType = :productType)
             group by p.id, p.code, p.name, p.cityName, month(s.salesDate)
             order by p.code, month(s.salesDate)
            """)
    List<AttendanceAgg> attendanceYearly(@Param("year") int year,
                                         @Param("grade") String grade,
                                         @Param("productType") String productType);

    /**
     * 발송 단위(일자·거래처·학교·분류)의 출고 창고. 작업결과의 '출고창고' 컬럼·필터.
     * 한 발송 건을 여러 번에 나눠 등록하면 창고가 섞일 수 있어 창고별로 행이 나온다(화면에서 합쳐 표기).
     * 창고가 기록되기 전(V40 이전) 매출은 warehouse가 null이라 제외된다.
     */
    @Query("""
            select s.salesDate as tradeDate, s.partner.id as partnerId,
                   coalesce(s.schoolCode, '') as schoolCode,
                   coalesce(p.salesDivision, '') as tradeClass,
                   w.id as warehouseId, w.name as warehouseName, w.type as warehouseType
              from Sale s join s.product p join s.warehouse w
             where s.canceled = false
               and s.salesCategory <> com.daesung.sales.salestype.entity.SalesCategory.RETURN
               and s.salesDate between :from and :to
             group by s.salesDate, s.partner.id, coalesce(s.schoolCode, ''),
                      coalesce(p.salesDivision, ''), w.id, w.name, w.type
            """)
    List<ShipmentWarehouseAgg> shipmentWarehouses(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
