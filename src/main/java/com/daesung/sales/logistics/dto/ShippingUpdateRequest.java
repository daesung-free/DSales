package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/**
 * 물류 발송정보 입력. 근거: 작업요청서.vb:915
 * {@code UPDATE sendData SET BoxCount=?, sendDate=?, sendMemo=? WHERE idx=?}.
 *
 * <p>지정하지 않은 항목은 건드리지 않는다 — 박스 수만 고치려다 발송일이 지워지면 안 된다.
 */
@Schema(name = "ShippingUpdateRequest", description = "발송정보 입력(박스 수·발송일·발송메모)")
public record ShippingUpdateRequest(
        @Schema(description = "박스 수", example = "3") @PositiveOrZero Integer boxCount,
        @Schema(description = "발송일", example = "2026-06-30") LocalDate sentDate,
        @Schema(description = "발송메모", example = "착불") String sendMemo
) {
}
