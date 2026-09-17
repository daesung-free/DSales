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
        @Schema(description = "마감(확정) 사유") String memo,

        @Schema(description = """
                마감 **해제** 사유. 확정 사유와 따로 남는다 —
                한 칸을 돌려쓰면 다시 마감했을 때 "왜 열었는지"가 사라진다.
                ‼️재마감해도 지워지지 않는다(한 번 열렸다는 사실이 감사 대상).""")
        String unlockMemo,
        @Schema(description = "마지막 해제자") String unlockedBy,
        @Schema(description = "마지막 해제 일시") LocalDateTime unlockedAt
) {
    public static PeriodLockResponse from(PeriodLock p) {
        return new PeriodLockResponse(
                p.getPeriodYear(), p.getPeriodMonth(), p.isLocked(),
                p.getLockedAt(), p.getLockedBy(), p.getMemo(),
                p.getUnlockMemo(), p.getUnlockedBy(), p.getUnlockedAt());
    }
}
