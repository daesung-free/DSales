package com.daesung.sales.audit.dto;

import com.daesung.sales.audit.entity.MasterChangeLog;
import com.daesung.sales.audit.entity.MasterEntityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 기초정보 변경 이력 1건(필드 하나의 변경). */
@Schema(name = "MasterChangeResponse", description = "기초정보 변경 이력")
public record MasterChangeResponse(
        @Schema(description = "이력 id") Long id,
        @Schema(description = "대상 종류") MasterEntityType entityType,
        @Schema(description = "대상 종류명") String entityTypeName,
        @Schema(description = "대상 id") Long entityId,
        @Schema(description = "대상 코드(거래처코드·도서코드)") String entityCode,
        @Schema(description = "필드명") String field,
        @Schema(description = "항목명(한글)") String fieldLabel,
        @Schema(description = "이전 값") String oldValue,
        @Schema(description = "이후 값") String newValue,
        @Schema(description = "변경자") String changedBy,
        @Schema(description = "변경 시각") LocalDateTime changedAt
) {
    public static MasterChangeResponse from(MasterChangeLog l) {
        return new MasterChangeResponse(l.getId(), l.getEntityType(),
                l.getEntityType() == null ? null : l.getEntityType().label(),
                l.getEntityId(), l.getEntityCode(), l.getField(), l.getFieldLabel(),
                l.getOldValue(), l.getNewValue(), l.getChangedBy(), l.getChangedAt());
    }
}
