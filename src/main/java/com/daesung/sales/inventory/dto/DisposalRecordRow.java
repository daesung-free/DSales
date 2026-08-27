package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 폐기 내역 한 줄(10p 조회). 필드 구성은 화면의 {@code DisposalRecord} 타입 그대로다.
 *
 * <p>★<b>등록만 되고 조회가 없었다.</b> 폐기는 재고를 깎는 전표라 "무엇을 언제 왜 버렸는지"를
 * 되짚을 수 없으면 재고가 안 맞을 때 원인을 찾을 방법이 없다.
 * 중간보고서 10p에 "도서별·창고별 조회 토글 구현"으로 나갔지만 서버에는 조회 경로가 없었다
 * (프론트 실호출 실측 §P-2에서 {@code GET /disposals} → 405로 확인).
 *
 * <p>원천은 재고이벤트({@code inventory_txn}, txn_type=DISPOSE)다 —
 * 별도 폐기 테이블을 두면 재고 단일공식(§재고 정의 단일화)이 깨진다.
 */
public record DisposalRecordRow(

        @Schema(description = "재고이벤트 id(화면의 disposalId)") Long disposalId,
        @Schema(description = "폐기번호(거래순번). 정본 10p 형식 P-yyyyMMdd-n", example = "P-20260626-1")
        String disposalNo,
        @Schema(description = "폐기일자") LocalDate date,
        @Schema(description = "창고 id") Long warehouseId,
        @Schema(description = "창고명") String warehouse,
        @Schema(description = "상품 id") Long productId,
        @Schema(description = "도서코드") String bookCode,
        @Schema(description = "도서명") String bookName,
        @Schema(description = "분류코드") String catCode,
        @Schema(description = "분류명") String catName,
        @Schema(description = "폐기수량(양수로 보여준다 — 원장은 음수로 기록된다)") int qty,
        @Schema(description = "폐기사유", example = "파본") String reason,
        @Schema(description = "비고") String memo
) {
}
