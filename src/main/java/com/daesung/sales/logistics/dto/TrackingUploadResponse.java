package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 송장번호 엑셀 일괄 등록 결과.
 *
 * <p>행별 결과를 돌려준다 — "몇 건 반영"만 보여주면 담당자는 어느 줄이 빠졌는지 모른 채
 * 파일 전체를 다시 봐야 한다(매출 업로드와 같은 규칙).
 */
@Schema(name = "TrackingUploadResponse", description = "송장번호 일괄 등록 결과")
public record TrackingUploadResponse(
        @Schema(description = "반영된 행 수") int updated,
        @Schema(description = "실패한 행 수") int failed,
        @Schema(description = "행별 결과") List<Line> lines
) {
    @Schema(name = "TrackingUploadLine")
    public record Line(
            @Schema(description = "엑셀 행 번호(1부터, 헤더 포함)") int rowNo,
            @Schema(description = "결과 UPDATED/ERROR") String status,
            @Schema(description = "발송 건 id") Long shipmentId,
            @Schema(description = "송장번호") String trackingNo,
            @Schema(description = "실패 사유(성공이면 null)") String message
    ) {
    }
}
