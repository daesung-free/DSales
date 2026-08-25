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
            @Schema(description = "정산상태명(미정산/부분정산/정산완료)", example = "부분정산") String statusName
    ) {
        /** 코드에서 한글명을 채워 생성 — 화면이 매번 매핑표를 들고 있지 않게. */
        public Line(Long consignmentOutId, String sourceOutNo, Long productId, String productCode,
                    String productName, int originalQty, int totalQty, int returnedQty,
                    int settledQty, int remainingQty, ConsignmentStatus status) {
            this(consignmentOutId, sourceOutNo, productId, productCode, productName,
                    originalQty, totalQty, returnedQty, settledQty, remainingQty, status,
                    status == null ? null : status.label());
        }
    }
}
