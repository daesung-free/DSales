package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.ContentType;
import com.daesung.sales.product.entity.MajorCategory;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.entity.SalesDivision;

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
        /** 세부구분 코드(구 '매출구분'). 저장되는 값은 이것 하나뿐이다. */
        String salesDivision,
        /** 세부구분 명칭. 마스터에서 따라온다. */
        String salesDivisionName,
        /** 대분류 — 세부구분 마스터에서 <b>파생</b>. 상품에 따로 저장하지 않는다(매핑은 한 곳에만). */
        MajorCategory majorCategory,
        /** 대분류 명칭(모의고사·교재 …). 프론트가 매핑표를 들고 있지 않게 같이 준다. */
        String majorCategoryName,
        Integer productYear,
        String productType,
        Integer supplyRate,
        boolean ledgerVisible,

        boolean priceVisible,
        boolean webVisible,
        boolean stockManaged
) {
    /**
     * @param division 이 상품의 세부구분 마스터. null이면 미지정이거나 마스터에 없는 값 →
     *                 대분류는 '미분류'(null)로 나간다. 조용히 '기타'로 바꾸지 않는다 —
     *                 바꿔버리면 매핑이 빠진 상품을 담당자가 영영 못 찾는다.
     */
    public static ProductResponse from(Product p, SalesDivision division) {
        MajorCategory major = (division == null) ? null : division.getMajorCategory();
        return new ProductResponse(
                p.getId(), p.getCode(), p.getName(), p.getContentType(),
                p.isSet(), p.getPrice(), p.isTaxFree(), p.getGrade(),
                p.getCatCode(), p.getCatName(), p.isUseYn(),
                p.getSalesDivision(),
                (division == null) ? null : division.getName(),
                major,
                (major == null) ? null : major.label(),
                p.getProductYear(), p.getProductType(), p.getSupplyRate(),
                p.isLedgerVisible(), p.isPriceVisible(), p.isWebVisible(), p.isStockManaged());
    }
}
