package com.daesung.sales.sale.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductRepository;
import com.daesung.sales.sale.dto.SaleResponse;
import com.daesung.sales.sale.dto.SalesEntryRequest;
import com.daesung.sales.sale.dto.SalesEntryResponse;
import com.daesung.sales.sale.entity.Sale;
import com.daesung.sales.sale.entity.SalesType;
import com.daesung.sales.sale.repository.SaleRepository;
import com.daesung.sales.salestype.entity.OutTypeMapping;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import com.daesung.sales.salestype.repository.OutTypeMappingRepository;
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

    /**
     * 수기 매출 등록(일반 매출). 품목마다 금액 산출 + 매출번호(I) 채번 후 매출 원장에 기록.
     * ※ 재고 반영은 여기서 하지 않음(주문/출고관리에서 통합 처리 예정). 위탁출고는 이 API 불가.
     */
    @Transactional
    public SalesEntryResponse createEntries(SalesEntryRequest req) {
        Partner partner = partnerRepository.findById(req.partnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + req.partnerId()));

        String datePart = req.salesDate().format(YYYYMMDD);
        List<SalesEntryResponse.Line> lines = new ArrayList<>();

        for (SalesEntryRequest.Item item : req.items()) {
            if (item.shipmentType() == ShipmentType.CONSIGN_SHIP) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "위탁출고 매출은 매출등록(위탁정산)으로 처리하세요.");
            }
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

            lines.add(new SalesEntryResponse.Line(salesNo, product.getId(), product.getCode(),
                    item.shipmentType(), salesCategory, item.qty(), supplyAmount, tax, totalAmount));
        }
        return new SalesEntryResponse(partner.getId(), partner.getName(), lines);
    }

    /** 매출 취소(논리 취소). 이미 취소된 건은 400. 재고 복구는 주문/출고 통합 단계에서. */
    @Transactional
    public SaleResponse cancel(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "매출이 없습니다. id=" + id));
        if (sale.isCanceled()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 취소된 매출입니다: " + sale.getSalesNo());
        }
        sale.cancel();
        return SaleResponse.from(sale);
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
}
