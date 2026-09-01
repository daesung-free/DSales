package com.daesung.sales.inventory.dto;

import com.daesung.sales.product.entity.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 제품수불부 <b>자재 상세</b> 한 줄(11p 2단계 조회).
 * 근거: 발주처 「도서관리·제품수불부현황 데이터 구조 보완 요청안」(2026-08-31).
 *
 * <p>문서의 표 그대로다 —
 * <pre>
 * 자재명   세트 출고분   회차 단독 출고분   합계(세트 내 소요량)
 * 시험지      500           100              600
 * </pre>
 *
 * <p>‼️<b>자재 재고가 아니다.</b> 원문: "자재 자체의 입고·이월을 반영한 재고 잔량이 아니라
 * 해당 세트 내 <b>소요량</b> 기준입니다." 즉 "이 세트가 팔리면서 자재가 몇 장 들어갔나"다.
 */
public record MaterialLedgerRow(

        @Schema(description = "자재 id") Long materialId,
        @Schema(description = "자재코드") String materialCode,
        @Schema(description = "자재명") String materialName,
        @Schema(description = "자재구분") MaterialType materialType,
        @Schema(description = "구성회차 — 공통이면 '공통'", example = "1회") String roundLabel,
        @Schema(description = "세트당 소요수량(매칭 시 입력한 값)") int qtyPerSet,
        @Schema(description = """
                세트 출고분 = 세트의 출고수량 × 세트당 소요수량.
                출고수량은 매출+무상+교사용+폐기 − 반품 ± 세트조립/해체(창고이동 제외).""")
        long fromSet,
        @Schema(description = """
                회차 단독 출고분 = 그 회차가 단품으로 나간 수량 × 소요수량.
                ★공통 자재는 0이다 — 회차 단독 판매 시 범용 자재가 몇 개 필요한지
                발주처 문서에 정의가 없다(확인 대기).""")
        long fromRound,
        @Schema(description = "합계 = 세트 출고분 + 회차 단독 출고분") long total
) {
}
