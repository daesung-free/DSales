package com.daesung.sales.sale.dto;

import com.daesung.sales.sale.entity.Sale;
import com.daesung.sales.sale.entity.SalesType;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import java.time.LocalDate;

/** 매출 상세/조회 응답 DTO. */
public record SaleResponse(
        Long id,
        String salesNo,
        LocalDate salesDate,
        Long partnerId,
        String partnerName,       // 거래처명2(합쳐진 풀네임)
        String partnerCityName,   // 도시명
        String partnerName1,      // 거래처명1(상호만)
        Long productId,
        String productCode,
        String productName,
        SalesType salesType,
        ShipmentType shipmentType,
        SalesCategory salesCategory,
        Integer unitPrice,
        Integer supplyRate,
        int qty,
        Long supplyAmount,
        Long tax,
        Long totalAmount,
        boolean canceled,
        String memo
) {
    public static SaleResponse from(Sale s) {
        return new SaleResponse(
                s.getId(), s.getSalesNo(), s.getSalesDate(),
                s.getPartner().getId(), s.getPartner().getName(),
                s.getPartner().getCityName(), s.getPartner().getName1(),
                s.getProduct().getId(), s.getProduct().getCode(), s.getProduct().getName(),
                s.getSalesType(), s.getShipmentType(), s.getSalesCategory(),
                s.getUnitPrice(), s.getSupplyRate(), s.getQty(),
                s.getSupplyAmount(), s.getTax(), s.getTotalAmount(),
                s.isCanceled(), s.getMemo());
    }
}
