package com.daesung.sales.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 제품수불부 <b>요약 → 회차 상세</b> 2단계 중 가운데 단계.
 * 근거: 발주처 구조보완요청안(2026-08-31) + 테스트 피드백 1차(2026-09-17) —
 * "수불현황 요약 각 행에 상세 진입 기능 추가. 현재는 세트 단위 집계 행만 조회되고
 *  세트-회차 계층 구조나 상세 진입 경로가 없음".
 *
 * <p>구조는 세 단계다.
 * <pre>
 *   요약   세트                    ← GET /stock/ledger
 *   중간   └ 회차                  ← **이 API**
 *   상세     └ 자재 소요량         ← GET /stock/ledger/materials
 * </pre>
 *
 * <p>★<b>회차 행은 수불부와 같은 값이다.</b> 여기서 다시 계산하지 않고
 * {@code stockLedger}를 회차 상품으로 좁혀 부른다 — 화면마다 계산식이 갈리면
 * 세트 합과 회차 합이 어긋난다(순매출수량 3,029/3,006 사고와 같은 이유).
 */
@Schema(name = "SetRoundLedgerResponse", description = "세트의 회차별 수불 현황")
public record SetRoundLedgerResponse(

        @Schema(description = "세트 상품 id") Long setProductId,
        @Schema(description = "세트 상품코드") String setProductCode,
        @Schema(description = "세트 상품명") String setProductName,

        @Schema(description = """
                구성 회차별 수불 행. **BOM에 등록된 회차만** 나온다 —
                거래가 없는 회차도 0으로 포함한다(빠지면 "이 회차는 왜 없지"가 된다).""")
        List<Round> rounds
) {

    @Schema(name = "SetRoundLedgerRow", description = "회차 한 줄")
    public record Round(

            @Schema(description = "회차 번호(BOM 등록값). 0이면 미지정") int round,

            @Schema(description = "세트 1개당 이 회차 소요수량(BOM 비율)") int ratioPerSet,

            @Schema(description = """
                    그 회차 상품의 수불 행. 창고가 여럿이면 여러 줄이다.
                    ★수불부 본 화면과 <b>같은 집계</b>다 — 여기서 다시 계산하지 않는다.""")
            List<StockLedgerRow> ledger
    ) {
    }
}
