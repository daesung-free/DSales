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
 * 기초정보 변경 이력 1건 = <b>필드 하나의 변경</b>.
 *
 * <p>저장 버튼 한 번에 전 필드를 남기지 않고 <b>실제로 달라진 필드만</b> 행을 만든다.
 * 전부 남기면 "이 값이 언제 바뀌었나"를 찾을 때 안 바뀐 행에 묻힌다.
 *
 * <p>추가만 하고 수정·삭제하지 않는다(감사 기록).
 */
@Entity
@Table(name = "master_change_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MasterChangeLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private MasterEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** 거래처코드·도서코드 등. id만 남기면 나중에 사람이 못 읽는다. */
    @Column(name = "entity_code", length = 100)
    private String entityCode;

    @Column(nullable = false, length = 50)
    private String field;

    @Column(name = "field_label", length = 100)
    private String fieldLabel;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public static MasterChangeLog of(MasterEntityType type, Long entityId, String entityCode,
                                     String field, String fieldLabel,
                                     String oldValue, String newValue, String changedBy) {
        MasterChangeLog l = new MasterChangeLog();
        l.entityType = type;
        l.entityId = entityId;
        l.entityCode = entityCode;
        l.field = field;
        l.fieldLabel = fieldLabel;
        // 값이 컬럼 길이를 넘으면 기록 자체가 실패해 원래 작업까지 막는다 — 잘라서라도 남긴다.
        l.oldValue = clip(oldValue);
        l.newValue = clip(newValue);
        l.changedBy = changedBy;
        l.changedAt = LocalDateTime.now();
        return l;
    }

    private static String clip(String v) {
        if (v == null) {
            return null;
        }
        return (v.length() <= 500) ? v : v.substring(0, 497) + "...";
    }
}
