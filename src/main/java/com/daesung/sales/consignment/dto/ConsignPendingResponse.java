package com.daesung.sales.consignment.dto;

import com.daesung.sales.consignment.entity.ConsignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 위탁 미결(잔여>0) 조회 결과. 정산 화면에서 거래처별로 불러온다. */
public record ConsignPendingResponse(

        @Schema(description = "위탁 거래처 id") Long partnerId,
        @Schema(description = "위탁 거래처명") String partnerName,
        @Schema(description = "미결 라인") List<Line> items
) {
    @Schema(name = "ConsignPendingLine")
    public record Line(
            @Schema(description = "미결(consignment_out) id") Long consignmentOutId,
            @Schema(description = "위탁출고번호", example = "OUT-20260608-1") String sourceOutNo,
            @Schema(description = "상품 id") Long productId,
            @Schema(description = "상품코드") String productCode,
            @Schema(description = "상품명") String productName,
            @Schema(description = """
                    원 출고수량(불변). 반품해도 줄지 않는다 —
                    **원출고 = 정산 + 반품 + 미결잔여**로 읽힌다.""")
            int originalQty,
            @Schema(description = "총 출고수량(아직 살아 있는 분). 반품하면 줄어든다") int totalQty,
            @Schema(description = "반품 누적 수량") int returnedQty,
            @Schema(description = "누적 정산수량") int settledQty,
            @Schema(description = "미결 잔여수량") int remainingQty,
            @Schema(description = "정산상태 코드(OPEN/PARTIAL/CLOSED)") ConsignmentStatus status,
            @Schema(description = "정산상태명(미정산/부분정산/정산완료)", example = "부분정산") String statusName,

            @Schema(description = """
                    출고 시점 정가(원). **원 출고건 값**이다 — 도서 마스터를 다시 읽지 않는다.
                    출고 뒤 정가가 바뀌어도 이 미결의 정산 금액은 흔들리지 않는다.
                    V62 이전에 생긴 미결은 null일 수 있다.""")
            Integer unitPrice,

            @Schema(description = "출고 시점 공급률(%). 담당자가 정산 시 수정할 수 있다(발주처 확정 2026-08-05 §2.2)")
            Integer supplyRate,

            @Schema(description = "출고 시점 할인액(원). 있으면 공급률 대신 금액에 쓰인다")
            Integer discountAmount,

            @Schema(description = """
                    **미결 잔여수량 기준** 공급가액(참고값). 서버가 금액 단일소스(Amounts)로 계산한다.
                    ‼️화면이 입력수량에 따라 미리보기를 만들 때 반올림이 달라질 수 있다 —
                    **확정 금액은 정산 시 서버가 다시 계산한 값**이다.
                    정가·공급률이 없는 옛 미결은 null.""")
            Long remainingSupplyAmount
    ) {
        /** 코드에서 한글명을 채워 생성 — 화면이 매번 매핑표를 들고 있지 않게. */
        public Line(Long consignmentOutId, String sourceOutNo, Long productId, String productCode,
                    String productName, int originalQty, int totalQty, int returnedQty,
                    int settledQty, int remainingQty, ConsignmentStatus status,
                    Integer unitPrice, Integer supplyRate, Integer discountAmount,
                    Long remainingSupplyAmount) {
            this(consignmentOutId, sourceOutNo, productId, productCode, productName,
                    originalQty, totalQty, returnedQty, settledQty, remainingQty, status,
                    status == null ? null : status.label(),
                    unitPrice, supplyRate, discountAmount, remainingSupplyAmount);
        }
    }
}
