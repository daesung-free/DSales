package com.daesung.sales.receivable.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 외상매출장(24p) — <b>도서 단위 명세 + 러닝밸런스</b>.
 * 근거: 레거시 외상매출장조회.vb {@code Refresh_DataGridView()}(기본 그리드) +
 * 정본 24p 주요 데이터 항목
 * "일자/분류명/도서명/공급%/매출수량액/세액/교사용수량액/반품수량액/수금액/잔액".
 *
 * <p>★예전엔 <b>전표 단위</b>로 {@code 일자·구분·전표번호·적요·금액·잔액} 6칸만 냈다.
 * 도서 정보가 적요 한 칸에 뭉개져 공급률·수량·세액을 볼 수 없었다 — 레거시·정본 둘 다
 * 도서 단위로 펼친다. 그 화면은 "얼마 받을 게 남았나"만이 아니라
 * <b>"무슨 책이 몇 권 얼마에 나갔나"</b>를 같이 보는 장부다.
 *
 * <p>★한 행에는 <b>해당 구분의 칸만</b> 채워진다(레거시 UNION 구조 그대로).
 * 매출 행이면 매출수량·매출금액·세액이 차고 교사용·반품·수금 칸은 비어 있다.
 */
@Schema(name = "ArLedgerResponse", description = "외상매출장(도서 단위 명세)")
public record ArLedgerResponse(
        @Schema(description = "거래처 id") Long partnerId,
        @Schema(description = "거래처명") String partnerName,
        @Schema(description = "조회 시작일") LocalDate fromDate,
        @Schema(description = "조회 종료일") LocalDate toDate,
        @Schema(description = "기초이월(시작일 직전까지 잔액)") long opening,
        @Schema(description = "기말잔액(누계 마지막)") long closing,
        @Schema(description = "명세 라인(일자순)") List<Line> lines
) {
    @Schema(name = "ArLedgerLine")
    public record Line(
            @Schema(description = "거래일자") LocalDate date,
            @Schema(description = "구분(매출/교사용/증정/반품/수금)") String kind,
            @Schema(description = "전표번호(매출번호 또는 수금번호)") String refNo,
            @Schema(description = "분류코드") String catCode,
            @Schema(description = "분류명") String catName,
            @Schema(description = "도서코드") String productCode,
            @Schema(description = """
                    도서명. 레거시처럼 회차·학교를 함께 붙인다 —
                    `도서명 [3회] <강남대성학원>`. 같은 책이 회차·학교별로 여러 줄 나오므로
                    이름만으로는 어느 줄인지 가릴 수 없다.""")
            String productName,
            @Schema(description = "공급률(%)") Integer supplyRate,

            @Schema(description = "매출수량(매출 행만)") Long saleQty,
            @Schema(description = "매출금액(공급가)") Long saleAmount,
            @Schema(description = "세액") Long tax,

            @Schema(description = """
                    교사용 수량. ‼️**공급률이 있는 무가만** 교사용으로 본다(레거시 `무상 AND supRate<>0`).
                    공급률 0인 무가는 증정이라 채권과 무관하다.""")
            Long teacherQty,
            @Schema(description = "교사용 금액") Long teacherAmount,

            @Schema(description = "반품수량(음수)") Long returnQty,
            @Schema(description = "반품금액(음수)") Long returnAmount,

            @Schema(description = "수금액(수금 행만)") Long collectAmount,

            @Schema(description = "채권 증감(매출·교사용 +, 반품·수금 −)") long amount,
            @Schema(description = "누계(러닝밸런스)") long balance
    ) {
    }
}
