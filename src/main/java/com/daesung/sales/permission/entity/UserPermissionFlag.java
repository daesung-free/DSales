package com.daesung.sales.permission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 개별 권한(발주처 3단계). 근거 원문 —
 * "④·⑤ 항목처럼 특정 담당자에게만 부여하는 권한은, 역할이 아니라
 * 사용자 ID 단위 Y/N 필드로 관리자가 직접 지정."
 *
 * <p>역할과 <b>무관하게</b> 붙는다. 같은 관리자라도 한 명은 마감확정 권한이 있고
 * 다른 한 명은 없을 수 있다(예시 시트의 mgr01=Y / mgr02=N).
 */
@Entity
@Table(name = "user_permission_flag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPermissionFlag {

    /** 마감 확정 권한. */
    public static final String PERIOD_LOCK = "PERIOD_LOCK";

    /** 마감 해제 권한 — 확정보다 민감해 따로 둔다. */
    public static final String PERIOD_UNLOCK = "PERIOD_UNLOCK";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "flag_key", nullable = false, length = 40)
    private String flagKey;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    public static UserPermissionFlag of(Long userId, String flagKey, boolean granted, String actor) {
        UserPermissionFlag f = new UserPermissionFlag();
        f.userId = userId;
        f.flagKey = flagKey;
        f.change(granted, actor);
        return f;
    }

    public void change(boolean granted, String actor) {
        this.granted = granted;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = actor;
    }
}
