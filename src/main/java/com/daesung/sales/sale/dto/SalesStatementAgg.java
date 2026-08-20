package com.daesung.sales.sale.dto;

import com.daesung.sales.product.entity.MajorCategory;

/**
 * 매출액명세서 집계 원천(도서 단위 flat). 근거: 레거시 매출액명세서.vb salesData 집계.
 * 대분류·분류·소계·총계 rollup은 서비스에서 이 flat 목록으로 조립.
 */
public interface SalesStatementAgg {

    /**
     * 대분류 — 상품의 세부구분이 물고 있는 값(발주처 회신 2026-08-20).
     * 세부구분 미지정이거나 마스터에 없는 값이면 null → '미분류'로 모인다.
     */
    MajorCategory getMajorCategory();

    String getCatCode();

    String getCatName();

    String getBookCode();

    String getBookName();

    long getQty();

    long getAmount();

    long getTax();
}
