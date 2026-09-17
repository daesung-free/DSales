package com.daesung.sales.inventory.dto;

import com.daesung.sales.inventory.entity.InboundType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/** 일반 입고 요청. 인쇄소 등(거래처) → 도착 창고로 입고. */
public record InboundRequest(

        @Schema(description = "처리일자", example = "2026-06-08", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = "입고 거래처(인쇄소 등) id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long supplierClientId,

        @Schema(description = "도착 창고 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long destinationWarehouseId,

        @Schema(description = """
                입고구분. 미지정 시 NORMAL.
                · `NORMAL` 정상입고(인쇄소 등 자체 제작·조달)
                · `PURCHASE` 매입입고 — **16p 순매출조회 매입액에 반영**
                · `RETURN` 반품입고 — 거래처에서 되돌아온 물량. **매입액에는 안 들어간다**
                  (반품으로 들어온 건 사 온 게 아니다).
                  ‼️매출 반품 라인까지 함께 만들려면 `POST /sales/return-inbound`를 쓴다 —
                  여기서는 재고만 늘고 매출은 건드리지 않는다.""",
                example = "NORMAL")
        InboundType inboundType,

        @Schema(description = "물류작업비 대상 여부(8p). 이 입고분을 물류 작업비 청구 대상으로 볼지",
                example = "false")
        Boolean logisCostTarget,

        @Schema(description = "입고 품목 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<InboundItem> items
) {
    /** 입고 품목. */
    public record InboundItem(

            @Schema(description = "상품 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long productId,

            @Schema(description = "입고단가(원가, 원)", example = "3500")
            Long unitCost,

            @Schema(description = "입고수량(양수)", example = "1000", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,

            @Schema(description = "비고", example = "초도 인쇄 물량")
            String memo
    ) {
    }
}
