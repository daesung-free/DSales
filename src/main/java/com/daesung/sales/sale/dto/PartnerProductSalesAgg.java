package com.daesung.sales.sale.dto;

/**
 * 거래처×상품 매출집계(SALE). 거래처별 매출대비표(전년 동기간 비교)의 원천.
 * 서비스에서 groupBy(거래처/분류/도서) 수준으로 롤업하고 당해·전년을 병합.
 */
public interface PartnerProductSalesAgg {
    Long getPartnerId();

    String getPartnerCode();

    String getPartnerName();

    String getCatCode();

    String getCatName();

    Long getProductId();

    String getBookCode();

    String getBookName();

    long getQty();

    long getAmount();
}
