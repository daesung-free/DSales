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

    /**
     * 권한 키 정규화.
     *
     * <p>‼️2026-09-11: 화면이 {@code periodLock}으로 보내는데 서버는 {@code PERIOD_LOCK}을 읽어
     * <b>200을 주면서 아무 효과가 없었다</b>. 조회 응답 필드명이 {@code periodLock}이라
     * 그 이름으로 되보내는 건 자연스러운 일이었는데, 저장 쪽이 문자열을 검증 없이 받았다.
     * 아무도 읽지 않는 칸에 저장되고 화면은 "저장됐다"고 알린다 — 가장 나쁜 실패다.
     *
     * <p>그래서 <b>두 표기를 모두 받고</b>, 모르는 키는 <b>거부한다</b>.
     * 받아 주기만 하고 거부하지 않으면 오타가 났을 때 같은 일이 또 난다.
     *
     * @throws BusinessException 아는 키가 아니면 INVALID_INPUT
     */
    private static final java.util.Map<String, String> KEY_ALIAS = java.util.Map.of(
            "PERIOD_LOCK", PERIOD_LOCK,
            "periodLock", PERIOD_LOCK,
            "period_lock", PERIOD_LOCK,
            "PERIOD_UNLOCK", PERIOD_UNLOCK,
            "periodUnlock", PERIOD_UNLOCK,
            "period_unlock", PERIOD_UNLOCK);

    public static String normalizeKey(String raw) {
        // ‼️대소문자 변환으로 맞추지 않는다. 권한 판단에 쓰는 값이라 로케일·유니코드 확장에
        //   따라 다른 문자열이 같아질 수 있다(정적분석 IMPROPER_UNICODE).
        //   받아들일 표기를 **명시적으로 나열**한다 — 무엇이 통과하는지가 코드에 그대로 보인다.
        String key = KEY_ALIAS.get((raw == null) ? "" : raw.trim());
        if (key == null) {
            throw new com.daesung.sales.common.exception.BusinessException(
                    com.daesung.sales.common.exception.ErrorCode.INVALID_INPUT,
                    "알 수 없는 권한 키입니다: " + raw + " (PERIOD_LOCK / PERIOD_UNLOCK)");
        }
        return key;
    }

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
