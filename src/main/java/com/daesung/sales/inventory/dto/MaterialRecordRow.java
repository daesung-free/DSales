package com.daesung.sales.inventory.dto;

import com.daesung.sales.product.entity.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 자재 거래 내역 한 줄.
 *
 * <p>근거: 9/27 회의 항목 22 — "도서관리-자재관리, 입고/대체등록에 <b>자재 등록한 내역이 보이게</b>".
 *
 * <p>도서 내역({@link StockRecordRow})과 칸을 맞췄다 — 같은 화면에서 탭만 바꿔 보는 표라
 * 컬럼 이름이 다르면 화면이 두 벌이 된다. 다른 것은 도서코드·분류 자리에
 * 자재코드·자재구분이 온다는 점이다.
 */
public record MaterialRecordRow(

        @Schema(description = "재고이벤트 id") Long recordId,
        @Schema(description = "처리일자") LocalDate date,
        @Schema(description = "작업구분(입고/단순이고/폐기/실사)", example = "입고") String kind,
        @Schema(description = "창고 id") Long warehouseId,
        @Schema(description = "창고명") String warehouse,
        @Schema(description = "자재 id") Long materialId,
        @Schema(description = "자재코드") String materialCode,
        @Schema(description = "자재명") String materialName,
        @Schema(description = "자재구분") MaterialType materialType,
        @Schema(description = "증감수량(부호 그대로 — 입고 +, 폐기·출발창고 −)") int qtyDelta,
        @Schema(description = "입고단가(원가). 입고 행에만 값이 있다") Long unitCost,
        @Schema(description = "전표번호") String refNo,
        @Schema(description = "비고") String memo
) {
}
