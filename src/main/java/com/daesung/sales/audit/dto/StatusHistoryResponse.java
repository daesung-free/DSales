package com.daesung.sales.audit.dto;

import com.daesung.sales.audit.entity.StatusEntityType;
import com.daesung.sales.audit.entity.StatusHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 상태변경 이력 1행(감사 조회). */
public record StatusHistoryResponse(
        @Schema(description = "이력 id") Long id,
        @Schema(description = "대상 종류") StatusEntityType entityType,
        @Schema(description = "대상 id") Long entityId,
        @Schema(description = "상태축") String field,
        @Schema(description = "이전 값") String fromStatus,
        @Schema(description = "이후 값") String toStatus,
        @Schema(description = "변경 사유") String reason,
        @Schema(description = "변경자") String changedBy,
        @Schema(description = "변경 시각") LocalDateTime changedAt
) {
    public static StatusHistoryResponse from(StatusHistory h) {
        return new StatusHistoryResponse(h.getId(), h.getEntityType(), h.getEntityId(), h.getField(),
                h.getFromStatus(), h.getToStatus(), h.getReason(), h.getChangedBy(), h.getChangedAt());
    }
}
