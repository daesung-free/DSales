package com.daesung.sales.dsre.gateway;

/**
 * DSRE2 매출일괄등록(교재) 대상 한 행(신청×분류×도서 피벗). 근거: 매출일괄등록_교재.vb 읽기쿼리.
 * M(매출)/J(증정)/B(무상) 3구분의 공급률·수량이 한 행에 피벗됨. 금액=정가×공급률/100×수량.
 */
public record BooklistImportRow(
        int reqCd,
        String custCode,   // machul_cd(매출코드) → 우리 거래처
        String custName,
        String lstCd,
        String dtlCd,
        String bookName,
        int price,         // 정가
        int saleRate, int saleQty,   // M 매출
        int giftRate, int giftQty,   // J 증정
        int freeRate, int freeQty,   // B 무상
        String memo
) {
}
