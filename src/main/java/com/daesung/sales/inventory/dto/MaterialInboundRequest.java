package com.daesung.sales.inventory.dto;

import com.daesung.sales.inventory.entity.InboundType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

/**
 * 자재 입고 요청(03 입고/대체).
 *
 * <p>근거: 9/27 회의 항목 6 — "도서·수불 표시 안되게. <b>자재별로 입고 가능하게!!!</b>" ·
 * 항목 5 "dsre는 매출만 변경, 매출에서는 수불·자재 다 관리".
 *
 * <p>도서 입고({@link InboundRequest})와 <b>요청 모양을 일부러 똑같이</b> 맞췄다.
 * 같은 화면에서 품목 종류만 바꿔 쓰는 동작이라, 필드 이름이 다르면 화면이 두 벌이 된다.
 * 다른 것은 {@code productId} 자리에 {@code materialId} 가 온다는 것 하나다.
 */
public record MaterialInboundRequest(

        @Schema(description = "처리일자", example = "2026-06-08", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate processedDate,

        @Schema(description = """
                입고 거래처(인쇄소·매입처 등) id. **선택이다** — 비워도 등록된다.
                근거: 현장 피드백 2026-10-14 「입고 거래처는 필수 아님 — 선택 없어도 등록되도록」.
                비우면 조회·수불부의 매입처 칸이 빈칸으로 나간다.""",
                example = "1")
        Long supplierClientId,

        @Schema(description = "도착 창고 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long destinationWarehouseId,

        @Schema(description = """
                입고구분. 미지정 시 NORMAL.
                자재는 되팔지 않으므로 실무상 대부분 NORMAL 이다 —
                `PURCHASE`(매입)는 16p 순매출조회 매입액에 잡히니 자재에는 쓰지 않는 것이 맞다.""",
                example = "NORMAL")
        InboundType inboundType,

        @Schema(description = """
                **도서(세트) id**. 발주처 1차회신 2026-10-02 #2 확정 흐름 —
                "도서 선택 → 하위 자재 수량 입력(DSRE식)".

                주면 두 가지를 서버가 검사한다 —
                · ★**DSRE 병행 상품이면 거부**한다. #1·#2 회신: "단독관리 상품=Y / 기존시스템(DSRE)
                  병행 상품=N", "DSRE 병행 상품은 조회만(자재 입출고는 DSRE에서 그대로 관리)".
                  판정은 도서의 **단가노출**(`priceVisible`)이다 — N 이면 DSRE 소관이라 막는다.
                  ‼️안 막으면 같은 자재 재고가 우리와 DSRE 두 곳에서 따로 움직인다.
                · 품목의 자재가 **그 도서의 구성 자재**인지. 아니면 거부한다 — 화면이 도서를 바꾼 뒤
                  이전 도서의 자재를 그대로 보내는 오입력을 막는다.

                비우면 예전처럼 자재를 직접 지정하는 입고다(검사 없음).""",
                example = "1")
        Long productId,

        @Schema(description = "입고 품목 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> items
) {
    /** 입고 자재. */
    @Schema(name = "MaterialInboundItem")
    public record Item(

            @Schema(description = "자재 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long materialId,

            @Schema(description = "입고단가(원가, 원)", example = "120")
            Long unitCost,

            @Schema(description = "입고수량(양수)", example = "5000", requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int qty,

            @Schema(description = "비고", example = "1회 시험지 인쇄 납품")
            String memo
    ) {
    }
}
