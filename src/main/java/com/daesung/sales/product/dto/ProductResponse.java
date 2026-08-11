package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.ContentType;
import com.daesung.sales.product.entity.Product;

/** 상품 응답 DTO. */
public record ProductResponse(
        Long id,
        String code,
        String name,
        ContentType contentType,
        boolean set,
        Integer price,
        boolean taxFree,
        String grade,
        String catCode,
        String catName,
        boolean useYn,
        String salesDivision,
        Integer productYear,
        String productType,
        Integer supplyRate,
        boolean ledgerVisible,
        boolean webVisible,
        boolean stockManaged
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(), p.getCode(), p.getName(), p.getContentType(),
                p.isSet(), p.getPrice(), p.isTaxFree(), p.getGrade(),
                p.getCatCode(), p.getCatName(), p.isUseYn(),
                p.getSalesDivision(), p.getProductYear(), p.getProductType(), p.getSupplyRate(),
                p.isLedgerVisible(), p.isWebVisible(), p.isStockManaged());
    }
}
