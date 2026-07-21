package com.daesung.sales.sale.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.closing.service.PeriodLockService;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.inventory.entity.TxnType;
import com.daesung.sales.inventory.service.InventoryService;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductRepository;
import com.daesung.sales.sale.dto.SaleResponse;
import com.daesung.sales.sale.dto.SalesEntryRequest;
import com.daesung.sales.sale.dto.SalesEntryResponse;
import com.daesung.sales.sale.dto.SalesSummaryResponse;
import com.daesung.sales.sale.dto.SalesSummaryRow;
import com.daesung.sales.sale.entity.Sale;
import com.daesung.sales.sale.entity.SalesType;
import com.daesung.sales.sale.repository.SaleRepository;
import com.daesung.sales.salestype.entity.OutTypeMapping;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import com.daesung.sales.salestype.repository.OutTypeMappingRepository;
import com.daesung.sales.warehouse.entity.Warehouse;
import com.daesung.sales.warehouse.repository.WarehouseRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SaleService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final SaleRepository saleRepository;
    private final PartnerRepository partnerRepository;
    private final ProductRepository productRepository;
    private final OutTypeMappingRepository outTypeMappingRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryService inventoryService;
    private final PeriodLockService periodLockService;

    /**
     * 수기 매출 등록(일반 매출) + 재고 반영을 한 트랜잭션으로. 품목마다 금액 산출 → 매출번호(I) 채번 →
     * 매출 원장 기록 + 물류창고 재고 반영(정상출고=−차감/음수재고 방지, 반품=+복구). 위탁출고는 이 API 불가.
     */
    @Transactional
    public SalesEntryResponse createEntries(SalesEntryRequest req) {
        periodLockService.assertNotLocked(req.salesDate());
        Partner partner = partnerRepository.findById(req.partnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + req.partnerId()));
        Warehouse warehouse = warehouseRepository.findById(req.warehouseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "물류창고가 없습니다. id=" + req.warehouseId()));

        String datePart = req.salesDate().format(YYYYMMDD);
        List<SalesEntryResponse.Line> lines = new ArrayList<>();

        for (SalesEntryRequest.Item item : req.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "상품이 없습니다. id=" + item.productId()));
            OutTypeMapping mapping = outTypeMappingRepository.findById(item.shipmentType())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                            "출고유형 매핑이 없습니다: " + item.shipmentType()));
            SalesCategory salesCategory = mapping.getSalesCategory();

            long supplyAmount = (long) ((double) item.unitPrice() * item.supplyRate() / 100.0 * item.qty());
            long tax = product.isTaxFree() ? 0L : supplyAmount / 10L;
            long totalAmount = supplyAmount + tax;

            String salesNo = "I-" + datePart + "-" + saleRepository.nextInvoiceSeq();

            Sale sale = Sale.create(salesNo, req.salesDate(), partner, product,
                    SalesType.NORMAL_SALES, item.shipmentType(), salesCategory,
                    item.unitPrice(), item.supplyRate(), item.qty(),
                    supplyAmount, tax, totalAmount, item.memo());
            saleRepository.save(sale);

            // 재고 반영(한 트랜잭션): 출고유형 → 부호/이벤트유형. 위탁·취소는 이 API 불가.
            int delta = stockDelta(item.shipmentType(), item.qty());
            TxnType txnType = (delta >= 0) ? TxnType.RETURN : TxnType.OUTBOUND;
            int stockBalance = inventoryService.applyShipment(product, warehouse, delta, txnType,
                    item.shipmentType(), req.salesDate(), salesNo, item.memo());

            lines.add(new SalesEntryResponse.Line(salesNo, product.getId(), product.getCode(),
                    item.shipmentType(), salesCategory, item.qty(), supplyAmount, tax, totalAmount, stockBalance));
        }
        return new SalesEntryResponse(partner.getId(), partner.getName(), lines);
    }

    /** 출고유형별 물류재고 증감 부호. 정상출고/증정/교사용=−차감, 반품=+복구. 위탁·취소는 이 API 불가. */
    private int stockDelta(ShipmentType shipmentType, int qty) {
        return switch (shipmentType) {
            case NORMAL_SHIP, GIFT, TEACHER_USE -> -qty;
            case RETURN -> qty;
            case CONSIGN_SHIP -> throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "위탁출고 매출은 위탁정산(/consignment/settle)으로 처리하세요.");
            case CANCEL -> throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "취소는 매출 취소(/sales/{id}/cancel)로 처리하세요.");
        };
    }

    /**
     * 매출 취소(논리 취소) + 재고 역분개를 한 트랜잭션으로. 이미 취소된 건은 400.
     * 원출고 이벤트(refNo=매출번호)를 반대 부호로 되돌려 재고 복구 + 수불부 버킷 상쇄.
     * 원출고 이벤트가 없으면(위탁정산 매출 등) 재고는 건드리지 않음.
     */
    @Transactional
    public SaleResponse cancel(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "매출이 없습니다. id=" + id));
        if (sale.isCanceled()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 취소된 매출입니다: " + sale.getSalesNo());
        }
        periodLockService.assertNotLocked(sale.getSalesDate());
        sale.cancel();
        // 취소 플래그를 먼저 확정(flush)하고 응답을 만든 뒤 역분개.
        // 역분개의 원자적 UPDATE(clearAutomatically)가 세션을 비우므로 순서가 중요.
        saleRepository.flush();
        SaleResponse response = SaleResponse.from(sale);
        inventoryService.reverseShipments(sale.getSalesNo(), LocalDate.now());
        return response;
    }

    /** 통합 매출 조회. */
    @Transactional(readOnly = true)
    public PageResponse<SaleResponse> search(LocalDate from, LocalDate to, SalesCategory salesCategory,
                                             ShipmentType shipmentType, Long partnerId,
                                             boolean includeCanceled, Pageable pageable) {
        return PageResponse.of(
                saleRepository.search(from, to, salesCategory, shipmentType, partnerId, includeCanceled, pageable)
                        .map(SaleResponse::from));
    }

    /**
     * 순매출 집계(상품별 + 합계행). 취소 제외. 매출/증정/교사용/반품 버킷 + 순매출(매출−반품).
     * 기간 미지정 시 올해 1/1~오늘.
     */
    @Transactional(readOnly = true)
    public SalesSummaryResponse summary(LocalDate fromDate, LocalDate toDate, Long partnerId) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();

        List<SalesSummaryRow> rows = new ArrayList<>();
        long tSaleQ = 0, tSaleA = 0, tFreeQ = 0, tFreeA = 0, tTchQ = 0, tTchA = 0,
                tRetQ = 0, tRetA = 0, tTax = 0, tTotal = 0;
        for (Object[] r : saleRepository.salesSummary(from, to, partnerId)) {
            long saleQty = num(r[3]), saleAmt = num(r[4]), freeQty = num(r[5]), freeAmt = num(r[6]),
                    tchQty = num(r[7]), tchAmt = num(r[8]), retQty = num(r[9]), retAmt = num(r[10]),
                    tax = num(r[11]), total = num(r[12]);
            rows.add(new SalesSummaryRow(
                    num(r[0]), (String) r[1], (String) r[2],
                    saleQty, saleAmt, freeQty, freeAmt, tchQty, tchAmt, retQty, retAmt,
                    saleQty - retQty, saleAmt - retAmt, tax, total));
            tSaleQ += saleQty; tSaleA += saleAmt; tFreeQ += freeQty; tFreeA += freeAmt;
            tTchQ += tchQty; tTchA += tchAmt; tRetQ += retQty; tRetA += retAmt; tTax += tax; tTotal += total;
        }
        SalesSummaryRow total = new SalesSummaryRow(null, "합계", null,
                tSaleQ, tSaleA, tFreeQ, tFreeA, tTchQ, tTchA, tRetQ, tRetA,
                tSaleQ - tRetQ, tSaleA - tRetA, tTax, tTotal);
        return new SalesSummaryResponse(from, to, rows, total);
    }

    private static long num(Object o) {
        return (o == null) ? 0L : ((Number) o).longValue();
    }
}
