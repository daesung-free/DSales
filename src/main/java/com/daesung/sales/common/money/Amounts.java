package com.daesung.sales.common.money;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;

/**
 * 매출 금액 계산 단일 소스.
 * <pre>
 *   금액(공급가액) = 정가 × 공급률/100 × 수량 (버림)
 *   세액           = 담당자가 입력한 값. 미입력이면 0
 *   총금액         = 금액 + 세액
 * </pre>
 *
 * <p>★load-bearing 계산식 — 매출등록·반품입고·위탁정산·매출일괄등록이 모두 이걸 호출한다.
 *
 * <p><b>세액은 자동산출하지 않는다</b>(발주처 확정 2026-07-28 재무팀 / 2026-08-05 문서 §1):
 * "세액이 붙는 상품이 거의 없어 자동산출 구조로 만들지 않음. 세액은 기본값 0, 필요 시 담당자가 직접 입력".
 * 이전에는 면세가 아니면 공급가액의 10%를 무조건 붙였으나, 실제 과세 건이 드물어 그 값이
 * 오히려 틀린 숫자로 흘러다녔다. 이제 입력하지 않으면 0이다.
 *
 * <p>면세 상품에 세액을 입력하면 명백한 오류이므로 거부한다 — 조용히 무시하면 담당자가
 * 입력한 값이 사라진 것을 모른다.
 *
 * <p>반올림: 버림(1원 단위 그대로, 반올림 없음 — 발주처 확정).
 */
public record Amounts(long supplyAmount, long tax, long totalAmount) {

    /** 공급가액 = 정가 × 공급률/100 × 수량 (버림). */
    public static long supplyOf(int unitPrice, int supplyRate, int qty) {
        return (long) ((double) unitPrice * supplyRate / 100.0 * qty);
    }

    /**
     * 금액 산출. 세액은 입력값 그대로 쓴다(미입력=0).
     *
     * @param tax      담당자가 입력한 세액. null이면 0
     * @param taxFree  면세 여부 — 면세인데 세액이 들어오면 거부한다
     */
    public static Amounts of(int unitPrice, int supplyRate, int qty, boolean taxFree, Integer tax) {
        long supply = supplyOf(unitPrice, supplyRate, qty);
        long t = (tax == null) ? 0L : tax;
        if (t != 0 && taxFree) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "면세 상품에는 세액을 입력할 수 없습니다. 입력값=" + t);
        }
        if (t < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "세액은 0 이상이어야 합니다. 입력값=" + t);
        }
        return new Amounts(supply, t, supply + t);
    }

    /** 세액 없이(0) 산출. 세액 입력 경로가 없는 자리에서 쓴다. */
    public static Amounts of(int unitPrice, int supplyRate, int qty, boolean taxFree) {
        return of(unitPrice, supplyRate, qty, taxFree, null);
    }
}
