package com.daesung.sales.batch.dto;

import com.daesung.sales.batch.entity.Notification;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 알림 1건(담보만기 팝업 등). */
public record NotificationResponse(
        @Schema(description = "알림 id") Long id,
        @Schema(description = "알림유형") String type,
        @Schema(description = "제목") String title,
        @Schema(description = "내용") String message,
        @Schema(description = "연관 대상 종류") String refType,
        @Schema(description = "연관 대상 id") Long refId,
        @Schema(description = "알림 발생 기준일") LocalDate notifyDate,
        @Schema(description = "확인 여부") boolean readYn
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getRefType(), n.getRefId(), n.getNotifyDate(), n.isReadYn());
    }
}
