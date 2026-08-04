package com.daesung.sales.audit.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상태변경 이력 1건. 근거: 개발문서 5.0 / 갭분석 BE-3A.
 *
 * <p><b>추가만 하고 수정·삭제하지 않는다.</b> 감사기록이 나중에 고쳐질 수 있으면 기록으로서 값이 없다.
 * 그래서 update/delete 메서드를 두지 않는다.
 */
@Entity
@Table(name = "status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StatusHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private StatusEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** 상태축. 한 대상에 상태가 여러 개일 수 있어 어느 축인지 구분한다. */
    @Column(nullable = false, length = 30)
    private String field;

    /** 이전 값. 최초 기록이면 null. */
    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    /** 변경 사유 — 감사에서 실제로 묻는 것은 "왜"다. */
    @Column(length = 500)
    private String reason;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public static StatusHistory of(StatusEntityType entityType, Long entityId, String field,
                                   String fromStatus, String toStatus, String reason, String changedBy) {
        StatusHistory h = new StatusHistory();
        h.entityType = entityType;
        h.entityId = entityId;
        h.field = field;
        h.fromStatus = fromStatus;
        h.toStatus = toStatus;
        h.reason = reason;
        h.changedBy = changedBy;
        h.changedAt = LocalDateTime.now();
        return h;
    }
}
