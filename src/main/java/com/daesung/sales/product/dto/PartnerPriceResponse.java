package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.ProductPartnerPrice;
import io.swagger.v3.oas.annotations.media.Schema;

/** 거래처별 단가·노출 매핑 응답. 단가는 도서 정가×공급률로 파생. */
public record PartnerPriceResponse(
        @Schema(description = "매핑 id") Long id,
        @Schema(description = "거래처 id") Long partnerId,
        @Schema(description = "거래처명") String partnerName,
        @Schema(description = "거래처 공급률(%)") Integer supplyRate,
        @Schema(description = "거래처별 노출 여부") boolean visible,
        @Schema(description = "파생 공급단가(정가×공급률/100, 정가·공급률 있을 때)") Long unitPrice
) {
    public static PartnerPriceResponse from(ProductPartnerPrice m) {
        Integer price = m.getProduct().getPrice();
        Integer rate = m.getSupplyRate();
        Long unitPrice = (price != null && rate != null) ? (long) price * rate / 100 : null;
        return new PartnerPriceResponse(
                m.getId(), m.getPartner().getId(), m.getPartner().getName(),
                rate, m.isVisible(), unitPrice);
    }
}
