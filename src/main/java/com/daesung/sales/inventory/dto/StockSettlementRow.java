package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 제품수불부 <b>결산내역</b> 한 행. 근거: 정본 11p 데이터 항목 "결산내역(연초~기준일 누적)" +
 * 레거시 {@code 제품수불부.Designer.vb:148} 체크박스 「결산내역」.
 *
 * <p>★레거시가 결산내역을 무엇으로 정의했는지가 그대로 답이다 — 화면 안내문 원문:
 * <i>"&lt;결산내역&gt; 체크시 기준일자 연도 1월1일부터 기준일자 까지의 제품수불 전체내역을 보여줍니다."</i>
 * 즉 <b>새 컬럼이 아니라 기간을 연초로 되감은 뷰</b>다. 체크하면 레거시는
 * {@code DateTimePicker_Start.Value = 기준일.ToString("yyyy.01.01")}로 시작일을 강제하고
 * ({@code 제품수불부.vb:1101}), 분류 선택을 잠근 뒤(전체 분류) 분류코드로 rollup한다
 * ({@code grouping(list.도서코드)} … '합 계' 행).
 *
 * <p>그래서 이 응답은 일반 수불부와 세 가지가 다르다 —
 * <ol>
 *   <li>기간을 받지 않는다. <b>기준일</b> 하나만 받고 시작일은 그 해 1월 1일로 고정한다.</li>
 *   <li>창고를 나누지 않고 합산한다(레거시엔 창고 축 자체가 없다). 창고구분 필터만 남긴다.</li>
 *   <li>분류 소계행과 총계행이 섞여 나온다({@link #rowType}).</li>
 * </ol>
 *
 * <p>이월은 "연초 이전 누계" = 사실상 <b>전기이월</b>이 된다. 그게 결산이 묻는 값이다.
 */
public record StockSettlementRow(
        @Schema(description = "행 구분(DETAIL=도서, CAT_SUBTOTAL=분류 합계, TOTAL=총계)")
        String rowType,
        @Schema(description = "분류코드(총계행 null)") String catCode,
        @Schema(description = "분류명(총계행 '총 계')") String catName,
        @Schema(description = "상품 id(소계·총계행 null)") Long productId,
        @Schema(description = "도서코드(소계·총계행 null)") String productCode,
        @Schema(description = "도서명(소계·총계행 null)") String productName,
        @Schema(description = "이월(연초 이전 누계 = 전기이월)") long opening,
        @Schema(description = "입고") long inbound,
        @Schema(description = "이고(순증감)") long transfer,
        @Schema(description = "조립/해체(순증감)") long bom,
        @Schema(description = "폐기(음수)") long dispose,
        @Schema(description = "매출출고(음수)") long sale,
        @Schema(description = "무상/증정(음수)") long free,
        @Schema(description = "교사용(음수)") long teacher,
        @Schema(description = "반품(양수)") long salesReturn,
        @Schema(description = "재고실사 조정(순증감)") long adjust,
        @Schema(description = "재고(기준일 현재)") long closing
) {

    /** 도서 상세행. */
    public static StockSettlementRow detail(String catCode, String catName, Long productId,
                                            String productCode, String productName, long[] b) {
        return new StockSettlementRow("DETAIL", catCode, catName, productId, productCode, productName,
                b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10]);
    }

    /** 분류 합계행. 레거시의 분류명 자리 '합 계'를 그대로 쓴다. */
    public static StockSettlementRow catSubtotal(String catCode, long[] b) {
        return new StockSettlementRow("CAT_SUBTOTAL", catCode, "합 계", null, null, null,
                b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10]);
    }

    /** 총계행. */
    public static StockSettlementRow total(long[] b) {
        return new StockSettlementRow("TOTAL", null, "총 계", null, null, null,
                b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10]);
    }
}
