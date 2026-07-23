package com.daesung.sales.sale.dto;

/**
 * 도서입출고현황의 매출측 집계(상품 단위). 근거: 레거시 도서입출고현황.vb salesData 매출/반품 버킷.
 * 매입측(입고/취소)·재고는 inventory_txn에서 별도 집계 후 서비스에서 상품키로 병합.
 */
public interface BookSalesAgg {
    Long getProductId();

    long getOutQty();

    long getOutAmt();

    long getRetQty();

    long getRetAmt();
}
