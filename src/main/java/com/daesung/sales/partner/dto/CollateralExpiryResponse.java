package com.daesung.sales.partner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 담보 만기 알림(만기 1개월 전 팝업). 근거: 재무팀 인터뷰 확정(부활).
 * 기준일 대비 담보 만기일이 withinDays 이내(또는 이미 만료)인 거래처 목록.
 */
public record CollateralExpiryResponse(
        @Schema(description = "기준일") LocalDate asOf,
        @Schema(description = "임박 판정 일수(기준일+이 일수 이내)") int withinDays,
        @Schema(description = "만기 임박/만료 거래처") List<Row> rows
) {
    @Schema(name = "CollateralExpiryRow")
    public record Row(
            @Schema(description = "거래처 id") Long partnerId,
            @Schema(description = "거래처코드") String code,
            @Schema(description = "거래처명") String name,
            @Schema(description = "담보 만기일") LocalDate assureExpiry,
            @Schema(description = "담보금액") Long assureAmount,
            @Schema(description = "만기까지 남은 일수(음수=이미 만료)") long daysUntilExpiry,
            @Schema(description = "상태(EXPIRED=만료/IMMINENT=임박)") String status
    ) {
    }
}
