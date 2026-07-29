package com.daesung.sales.sale.service;

import com.daesung.sales.dsre.gateway.BooklistImportRow;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductRepository;
import com.daesung.sales.sale.dto.BulkImportResponse;
import com.daesung.sales.sale.entity.Sale;
import com.daesung.sales.sale.repository.SaleRepository;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매출일괄등록(DSRE 교재). DSRE2에서 미처리(state='A') 읽어 매출 생성 + write-back(state='T').
 * 근거: 매출일괄등록_교재.vb. daesung.dsre.enabled=true일 때만.
 *
 * ★안전장치(레거시 결함 보완):
 *  - dryRun 미리보기(쓰기 없음)로 대상 확인 후 실행.
 *  - 소스키(req:lst:dtl:구분) UNIQUE 멱등 — write-back 실패해도 재실행 시 스킵(중복 매출 방지).
 *  - 매출 생성(Postgres) 후 write-back(MySQL). 분산트랜잭션 아님 → 멱등으로 방어.
 */
@Service
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
@RequiredArgsConstructor
public class BulkSalesImportService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final DsreGateway dsreGateway;
    private final SequenceService sequenceService;
    private final SaleRepository saleRepository;
    private final PartnerRepository partnerRepository;
    private final ProductRepository productRepository;
    private final com.daesung.sales.closing.service.PeriodLockService periodLockService;

    /** 구분: M(매출)·J(증정)·B(무상) → 출고유형/회계구분/공급률/수량. */
    private record Kind(String reqGn, ShipmentType shipmentType, SalesCategory category, int rate, int qty) {}

    @Transactional
    public BulkImportResponse importSales(LocalDate fromDate, LocalDate toDate, boolean dryRun) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();

        // 월마감 잠금 횡단검사(감사 결함 수정): 실제 적재(dryRun=false)만 — 마감월 매출 유입 차단.
        if (!dryRun) {
            periodLockService.assertNotLocked(to);
        }

        List<BulkImportResponse.Line> lines = new ArrayList<>();
        int imported = 0, skipped = 0, unmapped = 0;

        for (BooklistImportRow row : dsreGateway.readPendingBooklist(from, to)) {
            // 거래처(machul_cd)·도서(lst+dtl) → 우리 마스터 매핑
            Partner partner = partnerRepository.findByCode(row.custCode()).orElse(null);
            String productCode = row.lstCd() + row.dtlCd();
            Product product = productRepository.findByCode(productCode).orElse(null);

            boolean rowWritten = false;
            for (Kind k : List.of(
                    new Kind("M", ShipmentType.NORMAL_SHIP, SalesCategory.SALE, row.saleRate(), row.saleQty()),
                    new Kind("J", ShipmentType.GIFT, SalesCategory.FREE, row.giftRate(), row.giftQty()),
                    new Kind("B", ShipmentType.TEACHER_USE, SalesCategory.FREE, row.freeRate(), row.freeQty()))) {
                if (k.qty() <= 0) {
                    continue; // 수량 0 구분은 스킵
                }
                String sourceKey = row.reqCd() + ":" + row.lstCd() + ":" + row.dtlCd() + ":" + k.reqGn();
                long supply = com.daesung.sales.common.money.Amounts.supplyOf(row.price(), k.rate(), k.qty());

                if (partner == null || product == null) {
                    unmapped++;
                    lines.add(line(sourceKey, k.category(), row, k.qty(), supply, "UNMAPPED", null));
                    continue;
                }
                if (saleRepository.existsByBulkImportKey(sourceKey)) {
                    skipped++;
                    lines.add(line(sourceKey, k.category(), row, k.qty(), supply, "SKIPPED", null));
                    rowWritten = true; // 이미 등록된 적 있음 → write-back은 시도
                    continue;
                }
                if (dryRun) {
                    lines.add(line(sourceKey, k.category(), row, k.qty(), supply, "PREVIEW", null));
                    continue;
                }
                long tax = com.daesung.sales.common.money.Amounts.taxOf(supply, product.isTaxFree());
                String salesNo = "I-" + to.format(YYYYMMDD) + "-" + sequenceService.next(SequenceService.SEQ_INVOICE);
                saleRepository.save(Sale.createBulk(salesNo, to, partner, product,
                        k.shipmentType(), k.category(), row.price(), k.rate(), k.qty(),
                        supply, tax, supply + tax, row.memo(), sourceKey));
                imported++;
                rowWritten = true;
                lines.add(line(sourceKey, k.category(), row, k.qty(), supply, "IMPORTED", salesNo));
            }

            // write-back: 해당 도서행 처리 후 state='T'(중복방지). dryRun은 안 함.
            if (!dryRun && rowWritten) {
                dsreGateway.markBooklistDone(row.reqCd(), row.lstCd(), row.dtlCd());
            }
        }
        return new BulkImportResponse(dryRun, imported, skipped, unmapped, lines);
    }

    private static BulkImportResponse.Line line(String key, SalesCategory cat, BooklistImportRow row,
                                                int qty, long supply, String result, String salesNo) {
        return new BulkImportResponse.Line(key, cat.name(), row.custName(), row.bookName(), qty, supply, result, salesNo);
    }
}
