package com.daesung.sales.inventory.dto;

import com.daesung.sales.inventory.dto.StockWarning;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 폐기·입고 전표 취소 결과. */
@Schema(name = "VoucherCancelResponse", description = "전표 취소 결과(역분개)")
public record VoucherCancelResponse(

        @Schema(description = "취소한 전표번호", example = "P-20260917-1") String refNo,

        @Schema(description = "전표 종류 DISPOSE(폐기)/INBOUND(입고)") String voucherKind,

        @Schema(description = """
                되돌린 재고 이벤트 수. **0일 수 있다** — 재고 미관리 상품(모의고사 등)만 있던
                전표라는 뜻이고 오류가 아니다.""")
        int reversed,

        @Schema(description = "재고 경고(되돌린 뒤 음수가 되는 경우 등)") List<StockWarning> warnings
) {
}
