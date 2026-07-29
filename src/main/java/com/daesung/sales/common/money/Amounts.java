package com.daesung.sales.common.money;

/**
 * 매출 금액 계산 단일 소스. 공급가액=정가×공급률/100×수량(버림), 세액=면세면 0 아니면 공급가액의 10%(버림).
 * ★load-bearing 계산식 — 매출등록·반품입고·위탁정산·매출일괄등록이 모두 이걸 호출(4곳 복붙 제거).
 * ⚠️ 반올림 정책(현재 버림)·부가세율은 발주처 회신 대기 → 바뀌면 이 한 곳만 수정.
 */
public record Amounts(long supplyAmount, long tax, long totalAmount) {

    /** 부가세율(%). 세액 = 공급가액 × VAT_RATE/100 (정수 나눗셈 = 버림). */
    private static final int VAT_RATE = 10;

    /** 공급가액 = 정가 × 공급률/100 × 수량 (버림). */
    public static long supplyOf(int unitPrice, int supplyRate, int qty) {
        return (long) ((double) unitPrice * supplyRate / 100.0 * qty);
    }

    /** 세액 = 면세면 0, 아니면 공급가액의 10% (버림). */
    public static long taxOf(long supplyAmount, boolean taxFree) {
        return taxFree ? 0L : supplyAmount * VAT_RATE / 100;
    }

    public static Amounts of(int unitPrice, int supplyRate, int qty, boolean taxFree) {
        long supply = supplyOf(unitPrice, supplyRate, qty);
        long tax = taxOf(supply, taxFree);
        return new Amounts(supply, tax, supply + tax);
    }
}
