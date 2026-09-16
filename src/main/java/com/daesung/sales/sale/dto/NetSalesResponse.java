package com.daesung.sales.sale.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 콘텐츠구분 순매출. 자체교재(SELF)=매출−반품, 외부콘텐츠(EXTERNAL)=매출−매입=이익.
 * 매입원가는 매입입고(PURCHASE) unit_cost 가중평균. 근거: 순매출조회 + 콘텐츠구분 축 + 8p 매입입고 연동(2026-07-28).
 *
 * <p>정본 16p 데이터 항목 그대로다 —
 * {@code 상품분류/도서명, 매출(수량/공급가액), 교사용(증정용포함), 반품(수량/반품률/공급가액),
 * 순매출(수량/공급가액/세액/총금액), [외부콘텐츠] 입고/매입액, 이익금액/이익률}.
 *
 * <p>★<b>매입반품 칸은 두지 않았다</b>. 정본 16p 인터뷰(2026-07-25)가
 * "매입반품은 연중이 아니라 <b>연말에 이감과 일괄 계약</b>(원가×반품개수 단순 차감)"으로 닫았다 —
 * 기간 집계에 매달 채울 값이 아니라서, 칸만 만들면 늘 0이 찍혀 "반품이 없었다"로 읽힌다.
 */
public record NetSalesResponse(
        @Schema(description = "집계 시작일") LocalDate fromDate,
        @Schema(description = "집계 종료일") LocalDate toDate,
        @Schema(description = "콘텐츠구분 필터(SELF/EXTERNAL/전체)") String contentType,
        @Schema(description = "상품별 순매출") List<Row> rows,
        @Schema(description = "합계") Row total
) {
    @Schema(name = "NetSalesRow")
    public record Row(
            @Schema(description = "상품 id(합계행 null)") Long productId,
            @Schema(description = "분류코드(상품분류)") String catCode,
            @Schema(description = "분류명") String catName,
            @Schema(description = "상품코드") String productCode,
            @Schema(description = "상품명") String productName,
            @Schema(description = "콘텐츠구분 코드(SELF/EXTERNAL)") String contentType,
            @Schema(description = "콘텐츠구분 표기(자체교재/**매입 교재**). 발주처 2026-08-31 명칭 확정")
            String contentTypeName,
            @Schema(description = "매출수량") long saleQty,
            @Schema(description = "매출액(공급가액)") long saleAmount,
            @Schema(description = "교사용 수량(증정용 포함). `teacherQty`와 같은 값") long freeQty,
            @Schema(description = "교사용 공급가액(증정용 포함). `teacherAmount`와 같은 값") long freeAmount,
            @Schema(description = """
                    교사용 수량(증정용 포함) — `freeQty`와 **같은 값**이다.
                    ★이름을 하나 더 두는 이유: 정본 16p 컬럼명이 "교사용(증정용포함)"인데
                    서버 필드명이 `freeQty`라, 화면이 "무가 전체일 뿐 교사용은 따로 안 준다"고
                    읽고 칸을 비워 뒀다(2026-09-16 번들 실측 `absentColumns.teacherCnt`).
                    회계구분 FREE = 증정(GIFT) + 교사용(TEACHER_USE) 둘뿐이라 둘은 같은 집합이다.""")
            long teacherQty,
            @Schema(description = "교사용 공급가액(증정용 포함) — `freeAmount`와 같은 값")
            long teacherAmount,
            @Schema(description = "반품수량") long returnQty,
            @Schema(description = "반품률 %(반품수량/매출수량). 매출수량 0이면 null", example = "3.5")
            Double returnRate,
            @Schema(description = "반품액") long returnAmount,
            @Schema(description = "순매출수량(매출−반품)") long netQty,
            @Schema(description = "순매출액(매출−반품, 공급가액)") long netAmount,
            @Schema(description = "순매출 세액(매출세액−반품세액)") long netTax,
            @Schema(description = "순매출 총금액(순매출액+순매출세액)") long netTotal,
            @Schema(description = "매입 입고수량(기간 내 매입입고, 외부콘텐츠만)") Long inboundQty,
            @Schema(description = "매입단가(입고원가 평균, 외부콘텐츠만)") Long purchaseUnitCost,
            @Schema(description = "매입액(매입단가×순매출수량, 외부콘텐츠만)") Long purchaseAmount,
            @Schema(description = "이익(순매출액−매입액, 외부콘텐츠만)") Long profit,
            @Schema(description = "이익률 %(이익/순매출액, 외부콘텐츠만)") Double marginPct
    ) {
    }
}
