package com.daesung.sales.receivable.dto;

import com.daesung.sales.receivable.entity.CollectionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 수금관리(23p) 조회 — <b>소계가 끼워진 목록</b>. 근거: 레거시 수금관리.vb:146~227.
 *
 * <p>정본 23p는 "조회대상기준 3종(기장일자/수금일자/거래처(수금일기준))"과
 * "일자별 소계(일계) 자동 계산"을 요구한다. 레거시 실물은 <b>일계보다 더 촘촘하다</b> —
 * 날짜 기준에서는 일계·월계·누계 3단, 거래처 기준에서는 소계·누계가 붙는다.
 *
 * <p>이 응답은 <b>페이징하지 않는다</b>. 소계는 앞뒤 행이 다 있어야 성립해서,
 * 페이지를 자르면 잘린 지점의 소계가 틀린 값이 된다.
 * 등록·수정용 그리드는 페이징되는 {@code GET /closing/collections}를 쓴다.
 */
@Schema(name = "CollectionLedgerResponse", description = "수금 조회(소계 포함)")
public record CollectionLedgerResponse(
        @Schema(description = "조회대상기준") Basis basis,
        @Schema(description = "기준 설명") String basisName,
        @Schema(description = "시작일") LocalDate fromDate,
        @Schema(description = "종료일") LocalDate toDate,
        @Schema(description = "행 목록(명세 + 소계)") List<Row> rows,
        @Schema(description = "전체 합계") long total
) {
    /**
     * 조회대상기준 3종. 근거: 레거시 수금관리.vb:80~89 라디오 3개.
     * 기준마다 <b>날짜 필터 대상과 정렬이 함께 바뀐다</b>.
     */
    @Schema(name = "CollectionLedgerBasis")
    public enum Basis {
        /** 수금일자 기준 — collDate로 걸러 날짜순. 소계: 일계·월계·누계. */
        COLLECT_DATE("수금일자"),

        /** 기장일자 기준 — writeDate로 걸러 날짜순. <b>레거시 기본 선택</b>(수금관리.vb:38). */
        WRITE_DATE("기장일자"),

        /**
         * 거래처 기준 — 날짜 필터는 수금일자로 하되 거래처순으로 묶는다.
         * 소계: 거래처코드 <b>첫 글자</b> 그룹계·누계(레거시 {@code custCode.Substring(0,1)}).
         */
        PARTNER("거래처(수금일 기준)");

        private final String label;

        Basis(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Schema(name = "CollectionLedgerRow")
    public record Row(
            @Schema(description = "행 종류", allowableValues = {"DETAIL", "DAY_SUBTOTAL",
                    "MONTH_SUBTOTAL", "GROUP_SUBTOTAL", "RUNNING_TOTAL"})
            String rowType,
            @Schema(description = "소계 표시명(일 계·월 계·누 계·소 계). 명세행은 null") String label,
            @Schema(description = "수금 id(명세행만)") Long id,
            @Schema(description = "수금번호") String collectionNo,
            @Schema(description = "수금일자") LocalDate collDate,
            @Schema(description = "기장일자") LocalDate writeDate,
            @Schema(description = "거래처코드") String partnerCode,
            @Schema(description = "거래처명") String partnerName,
            @Schema(description = "수금구분(명목)") String collKind,
            @Schema(description = "입금구분 코드") CollectionType collType,
            @Schema(description = "입금구분 명칭") String collTypeName,
            @Schema(description = "금액") long collAmt,
            @Schema(description = "어음번호") String promissoryNo,
            @Schema(description = "만기일자") LocalDate promissoryDue,
            @Schema(description = "은행명") String bankName,
            @Schema(description = "지점명") String branchName,
            @Schema(description = "비고") String memo
    ) {
        /** 소계 행 — 금액과 표시명만 채운다. */
        public static Row subtotal(String rowType, String label, long amount) {
            return new Row(rowType, label, null, null, null, null, null, null, null, null, null,
                    amount, null, null, null, null, null);
        }
    }
}
