package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 재고 경고 1건. <b>막지 않고 알리기만 한다.</b>
 *
 * <p>근거 — 발주처 확정(2026-08-31 화면7 제품수불부):
 * "재고 음수 차단 로직은 적용되면 안됩니다. 입고 전 출고되는 상품은 재고 (−)로 처리되며,
 *  DSRE에서 수불관리하는 상품도 출고 수량만 나타나 재고가 마이너스로 표시되는 게 정상입니다."
 *
 * <p>‼️그래도 무검증은 위험하다 — 오타 하나로 999,999 폐기가 조용히 지나간다.
 * 그래서 처리는 진행하되 무슨 일이 있었는지 응답에 담는다.
 * <b>표시 방식(경고창/배지/색)은 발주처 회신 대기 중</b>이라, 서버는 사실만 주고 판단은 화면에 맡긴다.
 */
@Schema(name = "StockWarning", description = "재고 경고(차단 아님)")
public record StockWarning(
        @Schema(description = "경고 코드", example = "NEGATIVE_STOCK") String code,
        @Schema(description = "상품 id") Long productId,
        @Schema(description = "상품코드") String productCode,
        @Schema(description = "창고 id") Long warehouseId,
        @Schema(description = "창고명") String warehouseName,
        @Schema(description = "이번 증감(음수=차감)") int delta,
        @Schema(description = "처리 후 잔량(음수)") int balance,
        @Schema(description = "담당자에게 보여줄 문구") String message
) {
    public static StockWarning negative(Long productId, String productCode,
                                        Long warehouseId, String warehouseName,
                                        int delta, int balance) {
        return new StockWarning("NEGATIVE_STOCK", productId, productCode, warehouseId, warehouseName,
                delta, balance,
                "재고가 음수가 됩니다. " + warehouseName + " · " + productCode
                        + " 요청 " + (-delta) + " → 잔량 " + balance);
    }
}
