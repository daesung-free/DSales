package com.daesung.sales.sale.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 교재식 반품 가능내역 조회 응답. 거래처의 <b>도서 단위</b> 누적 매출·반품 내역.
 *
 * <p>★<b>행은 도서 하나에 하나다.</b> 예전에는 도서×정가×공급률 단위로 쪼개져 있었고
 * 이 주석도 그렇게 적혀 있었는데(2026-08-18 지적), 판정을 도서 단위로 바꾸면서
 * 서비스가 {@code byProduct}로 합산해 내보낸다 — <b>설명만 옛 구조로 남아 있었다.</b>
 * 같은 도서를 공급률 달리 여러 번 내보냈어도 한 줄로 합쳐진다.
 *
 * <p>반품가능수량의 바탕은 <b>확정매출(SALE) 수량</b>이다 — 출고수량이 아니다.
 * 위탁출고는 정산 전까지 매출이 아니므로 미결분은 여기 잡히지 않는다(정산돼야 반품 대상이 된다).
 *
 * <p>정가·공급률은 <b>원 출고건 값을 표시해 주기 위한 참고값</b>이며 반품 등록 시 수정할 수 있다
 * (발주처 확정 2026-08-05: "공급률은 원 출고건 값을 기준으로 표시하되 담당자가 필요 시 수정 가능").
 * 반품 범위 판정은 <b>도서 단위 합계</b>로 한다.
 */
public record ReturnableResponse(Long partnerId, List<Row> rows) {

    @Schema(name = "ReturnableRow")

    public record Row(
            Long productId,
            String productCode,
            String productName,
            Integer unitPrice,
            Integer supplyRate,
            @Schema(description = "누적 확정매출 수량(SALE). 위탁 미결분은 아직 매출이 아니라 포함되지 않는다")
            long saleQty,
            @Schema(description = "기(旣)반품 수량(RETURN)")
            long returnedQty,
            @Schema(description = "반품가능수량 = 확정매출 − 기반품")
            long returnableQty
    ) {
    }
}
