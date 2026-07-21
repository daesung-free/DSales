package com.daesung.sales.closing.dto;

import com.daesung.sales.closing.entity.PeriodLock;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 월마감 상태 응답. */
public record PeriodLockResponse(
        @Schema(description = "연도") int year,
        @Schema(description = "월") int month,
        @Schema(description = "마감(잠금) 여부") boolean locked,
        @Schema(description = "마감 일시") LocalDateTime lockedAt,
        @Schema(description = "마감자") String lockedBy,
        @Schema(description = "메모") String memo
) {
    public static PeriodLockResponse from(PeriodLock p) {
        return new PeriodLockResponse(
                p.getPeriodYear(), p.getPeriodMonth(), p.isLocked(),
                p.getLockedAt(), p.getLockedBy(), p.getMemo());
    }
}
