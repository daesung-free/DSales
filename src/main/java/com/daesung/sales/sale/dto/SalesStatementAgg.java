package com.daesung.sales.sale.dto;

/**
 * 매출액명세서 집계 원천(도서 단위 flat). 근거: 레거시 매출액명세서.vb salesData 집계.
 * 대분류(left(catCode,1))·분류·소계·총계 rollup은 서비스에서 이 flat 목록으로 조립.
 */
public interface SalesStatementAgg {
    String getCatCode();

    String getCatName();

    String getBookCode();

    String getBookName();

    long getQty();

    long getAmount();

    long getTax();
}
