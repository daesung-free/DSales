package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Map;

/**
 * 작업결과 1행 = 발송 건 하나. 레거시 작업결과.vb 컬럼 구성을 따랐다.
 *
 * <p>'출력'·'완료'는 레거시가 날짜 유무로 판단해 {@code ○}를 찍는다 —
 * 별도 상태 컬럼이 없다(정본이 "매출프로그램은 날짜플래그"라고 한 것이 이 화면이다).
 */
@Schema(name = "WorkResultRow", description = "작업결과 1행(발송 건)")
public record WorkResultRow(
        @Schema(description = "발송 건 id") Long id,
        @Schema(description = "분류", example = "IC") String tradeClass,
        @Schema(description = "거래일자") LocalDate tradeDate,
        @Schema(description = "거래순번") int tradeSeq,
        @Schema(description = "거래처코드") String partnerCode,
        @Schema(description = "거래처명") String partnerName,
        @Schema(description = "학교코드") String schoolCode,
        @Schema(description = "학교명") String schoolName,
        @Schema(description = "출력 여부(작업요청서 출력됨)") boolean printed,
        @Schema(description = "완료 여부. ⚠️레거시에 쓰기 경로가 없어 항상 false") boolean completed,
        @Schema(description = "발송일") LocalDate sentDate,
        @Schema(description = "출고창고(여러 창고에서 나갔으면 쉼표로 이어 붙인다). 창고 기록 전 매출은 비어 있다")
        String warehouseName,
        @Schema(description = "상품군별 수량(예: 교재 40, IC 20)") Map<String, Long> quantities,
        @Schema(description = "총 수량") long totalQty,
        @Schema(description = "박스 수") int boxCount,
        @Schema(description = "발송메모") String sendMemo,
        @Schema(description = "비고") String memo
) {
}
