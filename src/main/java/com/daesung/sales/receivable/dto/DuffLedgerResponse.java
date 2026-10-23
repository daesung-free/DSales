package com.daesung.sales.receivable.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 외상매출장 '더프모만'(24p) — <b>기본 외상매출장과 컬럼이 통째로 다르다</b>.
 *
 * <p>정본 24p: "'더프모만' 체크 시 그리드 컬럼 구조 자체가 학교(원)별/학년별/시행월별
 * 세분화 구조로 <b>완전 전환</b>됨(조건부 스키마)". 그래서 {@link ArLedgerResponse}에
 * 필드를 얹지 않고 별도 응답으로 낸다 — 한 응답에 두 스키마를 우겨넣으면
 * 절반이 늘 비어 있는 DTO가 된다.
 *
 * <pre>
 *   [기본]   일자 · 구분 · 전표번호 · 적요 · 금액 · 잔액        ← ArLedgerResponse
 *   [더프모] 일자 · 학교(원)명 · 시행월 · 학년 · 처리 · 정가 · 공급률 · 수량 · 금액
 * </pre>
 *
 * <p>기본 장부는 <b>채권 러닝밸런스</b>(매출·반품·수금을 합산한 잔액)를 보는 화면이고,
 * 더프모는 <b>모의고사 매출의 세부 내역</b>을 보는 화면이라 잔액 개념이 없다.
 * 레거시도 두 그리드를 완전히 다른 SQL로 만든다(외상매출장조회.vb:603).
 */
@Schema(name = "DuffLedgerResponse", description = "외상매출장 더프모만(24p) — 모의고사 세부 명세")
public record DuffLedgerResponse(
        @Schema(description = "거래처 id") Long partnerId,
        @Schema(description = "거래처명") String partnerName,
        @Schema(description = "조회 시작일") LocalDate fromDate,
        @Schema(description = "조회 종료일") LocalDate toDate,
        @Schema(description = "명세 + 소계(학교(원) 계 · 월 계)") List<Row> rows,

        @Schema(description = """
                <b>요약표</b> — 처리 구분별 수량·금액과 총계(마지막 행).

                근거: 레거시 `외상매출장조회.vb:778` 가 명세와 **별개 SQL**로
                `GROUP BY rollup(처리)` 를 떠서 두 번째 그리드(`DataGridView_요약`)에 물린다.
                그리고 더프모만 PDF 는 그 둘을 같이 찍는다(`ExportPDF_요약추가`, 1684행) —
                요약이 없으면 RESULT_ 양식이 반쪽이다.""")
        List<SummaryRow> summary,

        @Schema(description = "전체 수량 합") long totalQty,
        @Schema(description = "전체 금액 합") long totalAmount
) {
    @Schema(name = "DuffLedgerSummaryRow", description = "처리 구분별 합계 1행")
    public record SummaryRow(
            @Schema(description = """
                    처리 구분('처리'·'비처리'). 총계 행은 **null** 이다 — 레거시 rollup 의
                    총계 행도 처리 칸이 비어 있다.

                    ‼️레거시엔 'SOLID' 구분이 하나 더 있었지만 <b>우리 모델엔 그 값이 없다</b>
                    (`sale.proc_type` 는 GRADED/미지정 두 갈래다). 없는 구분을 빈 행으로
                    만들어 두지 않는다 — 늘 0인 행은 "아직 안 팔렸다"로 잘못 읽힌다.""")
            String procType,
            @Schema(description = "총계 행인가") boolean total,
            @Schema(description = "수량 합") long qty,
            @Schema(description = "금액 합") long amount
    ) {
    }

    @Schema(name = "DuffLedgerRow")
    public record Row(
            @Schema(description = "행 종류", allowableValues = {"DETAIL", "SCHOOL_SUBTOTAL", "MONTH_SUBTOTAL"})
            String rowType,
            @Schema(description = "소계 표시명('학교(원) 계'·'월 계'). 명세행은 null") String label,
            @Schema(description = "거래일자(명세행)") LocalDate date,
            @Schema(description = "학교(원)명") String schoolName,
            @Schema(description = """
                    시행월(yyyy-MM). 그 상품·회차의 **BOM 시행예정일**에서 온다.
                    ⚠️레거시는 도서명을 잘라 만들었고({@code LEFT(bookName, CHARINDEX('월', bookName))})
                    이름이 규칙에 안 맞으면 값이 깨졌다. 우리는 파싱하지 않으므로,
                    BOM에 시행예정일이 없으면 채우지 않고 **비운다**(엉뚱한 값보다 빈 값이 낫다).""")
            String examMonth,
            @Schema(description = "학년(상품 마스터)") String grade,
            @Schema(description = "처리 구분(처리/비처리). 매출등록의 성적처리 구분에서 온다") String procType,
            @Schema(description = "정가") Integer unitPrice,
            @Schema(description = "공급률(%)") Integer supplyRate,
            @Schema(description = "수량") long qty,
            @Schema(description = "금액(공급가)") long amount
    ) {
        /** 소계 행 — 수량·금액과 표시명만 채운다(레거시도 나머지 칸을 비운다). */
        public static Row subtotal(String rowType, String label, String schoolName,
                                   long qty, long amount) {
            return new Row(rowType, label, null, schoolName, null, null, null, null, null, qty, amount);
        }
    }
}
